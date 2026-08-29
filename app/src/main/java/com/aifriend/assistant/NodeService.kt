package com.aifriend.assistant

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Parcelable
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 核心无障碍服务
 *
 * 职责：
 * 1. 监听屏幕内容变化（TYPE_WINDOW_CONTENT_CHANGED）
 * 2. 调用 NodeDumper 抓取节点 XML
 * 3. 交给 NodePusher 推送给客户端
 *
 * 注意：
 * - dump 操作在 IO 线程，事件回调在主线程
 * - 100ms 节流，避免高频推送
 */
class NodeService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dumpJob: Job? = null

    companion object {
        private const val TAG = "NodeService"

        @Volatile
        var instance: NodeService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "无障碍服务已连接")
        NodePusher.attach(this)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.w(TAG, "无障碍服务解绑")
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext[Job]?.cancel()
        Log.w(TAG, "无障碍服务销毁")
    }

    /**
     * 屏幕内容变化事件
     * 事件驱动推送：只关心窗口内容变化和视图聚焦
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        Log.d(TAG, "onAccessibilityEvent type=$eventType pkg=${event.packageName}")

        if (!NodePusher.isStarted()) {
            Log.w(TAG, "NodePusher 未启动，跳过")
            return
        }

        when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                scheduleDump()
            }
            else -> {
                Log.d(TAG, "忽略事件类型 $eventType")
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "服务中断")
    }

    /**
     * 节流 dump：100ms 内多次事件合并为一次
     */
    private fun scheduleDump() {
        if (dumpJob?.isActive == true) return
        dumpJob = scope.launch {
            delay(100)  // 节流
            performDump()
        }
    }

    /**
     * 实际执行 dumpXml 并推送
     */
    private suspend fun performDump() {
        val svc = instance ?: return
        val xml = NodeDumper.dumpService(svc)
        if (xml.isNullOrEmpty()) {
            Log.w(TAG, "dumpXml 返回空")
            return
        }
        Log.i(TAG, "dumpXml 成功, size=${xml.length}")
        NodePusher.broadcast(xml)
    }

    /**
     * Parcelable 占位（保留以便后续扩展）
     */
    @Suppress("unused")
    private val parcelable: Parcelable? = null

    @Suppress("unused")
    private fun apiLevel(): Int = Build.VERSION.SDK_INT
}