package com.aifriend.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 开机自启广播
 *
 * 触发时机：
 * - 设备开机完成（BOOT_COMPLETED）
 * - 适用于：用户希望【小a】开机即自动启动
 *
 * 注意：
 * - 用户必须在系统设置里手动开启【小a】的自启动权限，否则这个 Receiver 不会触发
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val action = intent?.action ?: return

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i(TAG, "开机自启触发: $action")
            try {
                // 启动前台服务
                AssistForegroundService.start(context)

                // 尝试拉起 MainActivity（部分 ROM 需要）
                val main = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ 不允许后台启动 Activity，此处仅尝试，可能失败
                    try {
                        context.startActivity(main)
                    } catch (e: Exception) {
                        Log.w(TAG, "启动 MainActivity 失败（忽略）", e)
                    }
                } else {
                    context.startActivity(main)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "开机自启失败", t)
            }
        }
    }
}