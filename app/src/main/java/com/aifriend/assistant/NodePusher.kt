package com.aifriend.assistant

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.MutableLiveData
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * LocalSocket 服务端
 *
 * 协议设计：
 * - 服务端：Unix Domain Socket（LocalServerSocket），名称 "aifriend_assistant"
 * - 客户端可使用 adb forward tcp:9000 localabstract:aifriend_assistant 映射
 * - 每条消息格式：[4字节大端长度][UTF-8消息体]
 * - 消息体类型：
 *   1. <dump>...</dump>    节点 XML
 *   2. <ping/>              心跳
 *   3. <error>...</error>   错误
 *
 * 设计要点：
 * - 单独线程 accept 连接
 * - 客户端列表 CopyOnWriteArrayList 保证并发安全
 * - 死连接自动清理
 */
object NodePusher {

    private const val TAG = "NodePusher"
    private const val SOCKET_NAME = "aifriend_assistant"
    private const val PING_INTERVAL_MS = 5000L

    @Volatile
    private var service: AccessibilityService? = null

    @Volatile
    private var serverSocket: AutoCloseable? = null  // 实际是 LocalServerSocket，反射创建
    @Volatile
    private var acceptThread: Thread? = null
    @Volatile
    private var pingThread: Thread? = null

    private val clients = CopyOnWriteArrayList<ClientConn>()
    private val mainHandler = Handler(Looper.getMainLooper())

    // 调试状态：暴露给 ViewModel
    val clientCountLiveData = MutableLiveData<Int>(0)

    /**
     * 客户端连接封装
     */
    private class ClientConn(
        val output: OutputStream,
        val socket: Socket,
        val name: String
    )

    /**
     * 启动 Pusher（由 NodeService.onServiceConnected 调用）
     */
    fun attach(svc: AccessibilityService) {
        if (service != null) return
        service = svc
        startServer()
        startPing()
    }

    /**
     * 停止 Pusher
     */
    fun detach() {
        stopServer()
        stopPing()
        service = null
    }

    /**
     * 是否已启动
     */
    fun isStarted(): Boolean = serverSocket != null

    /**
     * 启动 LocalServerSocket（用反射，因为 LocalServerSocket 在 android.net 包，
     * import 时 IDE 不会报错，但 standalone import 在某些 Gradle 配置下会失败）
     */
    private fun startServer() {
        if (serverSocket != null) return
        acceptThread = Thread({
            try {
                val cls = Class.forName("android.net.LocalServerSocket")
                val ctor = cls.getConstructor(String::class.java)
                serverSocket = ctor.newInstance(SOCKET_NAME) as AutoCloseable
                Log.i(TAG, "LocalServerSocket 启动: $SOCKET_NAME")

                // accept() 是阻塞调用，需要反射调用
                val acceptMethod = cls.getMethod("accept")
                val getOutputStreamMethod = java.net.Socket::class.java.getMethod("getOutputStream")

                while (!Thread.currentThread().isInterrupted) {
                    val sock = acceptMethod.invoke(serverSocket) as Socket
                    val out = getOutputStreamMethod.invoke(sock) as OutputStream
                    val client = ClientConn(out, sock, "client-${System.currentTimeMillis()}")
                    clients.add(client)
                    updateClientCount()
                    Log.i(TAG, "客户端连接: ${client.name}, 总数: ${clients.size}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "accept 异常", t)
            }
        }, "NodePusher-Accept").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * 停止服务端
     */
    private fun stopServer() {
        try {
            acceptThread?.interrupt()
            serverSocket?.close()
        } catch (_: Throwable) {}
        serverSocket = null
        acceptThread = null

        // 关闭所有客户端
        clients.forEach { conn ->
            try { conn.output.close() } catch (_: Throwable) {}
            try { conn.socket.close() } catch (_: Throwable) {}
        }
        clients.clear()
        updateClientCount()
    }

    /**
     * 心跳线程：每 5s 推送 <ping/>，同时清理已断开的客户端
     */
    private fun startPing() {
        pingThread = Thread({
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(PING_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
                broadcastInternal("<ping/>".toByteArray(Charsets.UTF_8), isPing = true)
            }
        }, "NodePusher-Ping").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopPing() {
        pingThread?.interrupt()
        pingThread = null
    }

    /**
     * 广播 XML 节点
     */
    fun broadcast(xml: String) {
        val body = ("<dump>" + xml + "</dump>").toByteArray(Charsets.UTF_8)
        broadcastInternal(body, isPing = false)

        // 通知调试页
        mainHandler.post {
            DebugBus.notifyPush(body.size, clients.size)
        }
    }

    /**
     * 实际广播逻辑
     */
    private fun broadcastInternal(data: ByteArray, isPing: Boolean) {
        val header = intToBytes(data.size)
        val payload = header + data

        // 先 copy 一份当前列表
        val snapshot = clients.toList()
        val dead = mutableListOf<ClientConn>()

        for (c in snapshot) {
            try {
                synchronized(c.output) {
                    c.output.write(payload)
                    c.output.flush()
                }
            } catch (e: IOException) {
                dead.add(c)
            } catch (t: Throwable) {
                Log.w(TAG, "发送失败: ${c.name}", t)
                dead.add(c)
            }
        }

        if (dead.isNotEmpty()) {
            clients.removeAll(dead.toSet())
            dead.forEach { conn ->
                try { conn.output.close() } catch (_: Throwable) {}
                try { conn.socket.close() } catch (_: Throwable) {}
            }
            updateClientCount()
            if (!isPing) Log.i(TAG, "清理 ${dead.size} 个死连接, 剩余: ${clients.size}")
        }
    }

    private fun updateClientCount() {
        mainHandler.post {
            clientCountLiveData.value = clients.size
            DebugBus.notifyClientCount(clients.size)
        }
    }

    /**
     * int 转 4 字节大端
     */
    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 24 and 0xFF).toByte(),
            (value shr 16 and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }
}

/**
 * 调试事件总线：把 NodePusher 的状态变更传递给 DebugViewModel
 *
 * 设计原因：NodePusher 是单例 object，DebugViewModel 是 Activity 级，
 * 用静态回调避免 LiveData 跨进程同步问题
 */
object DebugBus {
    @Volatile var listener: ((packetSize: Int, clientCount: Int) -> Unit)? = null
    @Volatile var clientCountListener: ((Int) -> Unit)? = null

    fun notifyPush(packetSize: Int, clientCount: Int) {
        listener?.invoke(packetSize, clientCount)
    }

    fun notifyClientCount(count: Int) {
        clientCountListener?.invoke(count)
    }
}