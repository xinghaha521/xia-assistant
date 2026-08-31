package com.aifriend.assistant

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.MutableLiveData
import java.io.IOException
import java.io.OutputStream
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
 *
 * 设计要点：
 * - 单独线程 accept 连接
 * - 用反射创建 LocalServerSocket（android.net.LocalServerSocket 与 LocalSocket）
 * - LocalSocket 不能转成 java.net.Socket（ClassCastException），必须用原生的 LocalSocket
 * - 客户端列表 CopyOnWriteArrayList 保证并发安全
 * - 死连接自动清理
 */
object NodePusher {

    private const val TAG = "NodePusher"
    private const val SOCKET_NAME = "aifriend_assistant"
    private const val PING_INTERVAL_MS = 5000L

    @Volatile
    private var service: AccessibilityService? = null

    // 直接持有反射创建的 LocalServerSocket（其实是 AutoCloseable）
    @Volatile
    private var serverSocket: Any? = null
    @Volatile
    private var acceptThread: Thread? = null
    @Volatile
    private var pingThread: Thread? = null

    // 反射缓存（性能优化）
    private var acceptMethod: java.lang.reflect.Method? = null
    private var localSocketOutputStreamMethod: java.lang.reflect.Method? = null
    private var localSocketCloseMethod: java.lang.reflect.Method? = null

    private val clients = CopyOnWriteArrayList<ClientConn>()
    private val mainHandler = Handler(Looper.getMainLooper())

    // 调试状态：暴露给 ViewModel
    val clientCountLiveData = MutableLiveData<Int>(0)

    /**
     * 推送事件（packetSize, clientCount）
     */
    data class PushEvent(val packetSize: Int, val clientCount: Int)
    val pushEventLiveData = MutableLiveData<PushEvent>()

    /**
     * 客户端连接封装
     * localSocket 是反射得到的 android.net.LocalSocket 实例
     */
    private class ClientConn(
        val output: OutputStream,
        val localSocket: Any,  // android.net.LocalSocket
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
        startForcedDump()  // 调试：主动 3 秒 dump 一次
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
     * 启动 LocalServerSocket（全部用反射，避开 android.net 包 import）
     */
    private fun startServer() {
        if (serverSocket != null) return

        // 反射拿 LocalServerSocket 类
        val serverCls = Class.forName("android.net.LocalServerSocket")
        val socketCls = Class.forName("android.net.LocalSocket")

        acceptMethod = serverCls.getMethod("accept")
        localSocketOutputStreamMethod = socketCls.getMethod("getOutputStream")
        localSocketCloseMethod = socketCls.getMethod("close")

        acceptThread = Thread({
            try {
                val ctor = serverCls.getConstructor(String::class.java)
                serverSocket = ctor.newInstance(SOCKET_NAME)
                Log.i(TAG, "LocalServerSocket 启动: $SOCKET_NAME")

                while (!Thread.currentThread().isInterrupted) {
                    val localSock = acceptMethod!!.invoke(serverSocket)  // android.net.LocalSocket 实例
                    val out = localSocketOutputStreamMethod!!.invoke(localSock) as OutputStream
                    val client = ClientConn(out, localSock, "client-${System.currentTimeMillis()}")
                    clients.add(client)
                    updateClientCount()
                    Log.i(TAG, "客户端连接: ${client.name}, 总数: ${clients.size}")
                }
            } catch (t: Throwable) {
                if (t !is java.io.IOException) {
                    Log.e(TAG, "accept 异常", t)
                }
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
            serverSocket?.let {
                (it as? AutoCloseable)?.close()
            }
        } catch (_: Throwable) {}
        serverSocket = null
        acceptThread = null

        // 关闭所有客户端
        clients.forEach { conn ->
            try { conn.output.close() } catch (_: Throwable) {}
            try { localSocketCloseMethod?.invoke(conn.localSocket) } catch (_: Throwable) {}
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
     * 调试用：主动每 3 秒 dump 一次（绕过事件触发问题）
     * 验证用：确认 dump + broadcast 链路是否 OK
     */
    private fun startForcedDump() {
        val t = Thread({
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(3000)
                } catch (_: InterruptedException) { break }
                val svc = service ?: continue
                val xml = NodeDumper.dumpService(svc)
                if (!xml.isNullOrEmpty()) {
                    Log.i(TAG, "主动 dump 成功, size=${xml.length}")
                    broadcast(xml)
                }
            }
        }, "NodePusher-ForcedDump").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * 广播 XML 节点
     */
    fun broadcast(xml: String) {
        val body = ("<dump>" + xml + "</dump>").toByteArray(Charsets.UTF_8)
        broadcastInternal(body, isPing = false)

        mainHandler.post {
            pushEventLiveData.value = PushEvent(body.size, clients.size)
        }
    }

    /**
     * 实际广播逻辑
     */
    private fun broadcastInternal(data: ByteArray, isPing: Boolean) {
        val header = intToBytes(data.size)
        val payload = header + data

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
                try { localSocketCloseMethod?.invoke(conn.localSocket) } catch (_: Throwable) {}
            }
            updateClientCount()
            if (!isPing) Log.i(TAG, "清理 ${dead.size} 个死连接, 剩余: ${clients.size}")
        }
    }

    private fun updateClientCount() {
        mainHandler.post {
            clientCountLiveData.value = clients.size
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