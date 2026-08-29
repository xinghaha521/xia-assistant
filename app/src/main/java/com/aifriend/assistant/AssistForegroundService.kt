package com.aifriend.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * 前台服务
 *
 * 作用：
 * 1. 提升进程优先级，避免被系统杀死
 * 2. 用户可在通知栏看到【小a】正在运行
 * 3. 长期持有，保持 LocalSocket 服务端存活
 *
 * 启动方式：
 * - 用户手动点击 MainActivity 中的"启动前台服务"
 * - 开机自启后由 BootReceiver 启动
 */
class AssistForegroundService : Service() {

    companion object {
        private const val TAG = "AssistForegroundService"
        private const val CHANNEL_ID = "xia_assistant"
        private const val NOTIFY_ID = 1001

        fun start(ctx: Context) {
            val intent = Intent(ctx, AssistForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, AssistForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "前台服务创建")
        createNotificationChannel()
        startForeground(NOTIFY_ID, buildNotification())
        DebugBus.listener?.let { /* keepalive */ }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "前台服务 onStartCommand")
        // 返回 START_STICKY：被系统杀后自动重启
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "前台服务销毁")
    }

    /**
     * 创建通知渠道（Android 8+ 必须）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "节点服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AI 节点服务正在后台运行"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * 构建前台服务通知
     */
    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("【小a】节点服务运行中")
            .setContentText("LocalSocket: aifriend_assistant")
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}