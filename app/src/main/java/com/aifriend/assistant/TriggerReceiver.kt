package com.aifriend.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 接收外部 trigger 指令
 * 用法：
 *   adb shell am broadcast -a com.aifriend.assistant.TRIGGER_ASSIST -n com.aifriend.assistant/.TriggerReceiver
 */
class TriggerReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "TriggerReceiver"
        const val ACTION = "com.aifriend.assistant.TRIGGER_ASSIST"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.i(TAG, "收到外部 trigger 指令")
            val svc = VoiceCommandService.instance
            if (svc != null) {
                svc.triggerNewSession()
            } else {
                Log.w(TAG, "VoiceCommandService 尚未就绪")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "trigger 失败: ${t.message}", t)
        }
    }
}