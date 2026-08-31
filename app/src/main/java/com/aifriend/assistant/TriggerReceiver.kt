package com.aifriend.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 接收外部 trigger 指令
 *
 * v0.8.0 起支持运行模式分发：
 * - 数字模式 → VoiceCommandService.triggerNewSession()
 * - 无障碍模式 → NodeService.triggerDump()
 *
 * 用法：
 *   adb shell am broadcast -a com.aifriend.assistant.TRIGGER_ASSIST
 *     -n com.aifriend.assistant/.TriggerReceiver
 *
 * 注意：
 * - 必须用 -n 指定 ComponentName，触发显式广播
 * - 必须用 goAsync() 防止 ANR（onReceive 在主线程）
 */
class TriggerReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "TriggerReceiver"
        const val ACTION = "com.aifriend.assistant.TRIGGER_ASSIST"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        try {
            if (XiaSettings.isAccessibilityMode(context)) {
                Log.i(TAG, "收到 trigger（无障碍模式）")
                NodeService.triggerDump(context)
            } else {
                Log.i(TAG, "收到 trigger（数字模式）")
                val svc = VoiceCommandService.instance
                if (svc != null) {
                    svc.triggerNewSession()
                    Log.i(TAG, "trigger 已下发到 VoiceCommandService")
                } else {
                    Log.w(TAG, "VoiceCommandService 尚未就绪，无法 trigger")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "trigger 失败: ${t.message}", t)
        } finally {
            pending.finish()
        }
    }
}
