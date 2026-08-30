package com.aifriend.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.util.Log

class VoiceCommandService : VoiceInteractionService() {

    companion object {
        private const val TAG = "VoiceCommandService"
        private const val SHOW_FLAGS_TRIGGER = 9
        private const val TRIGGER_DELAY_MS = 2000L
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val triggerRunnable = object : Runnable {
        override fun run() {
            try {
                val args = Bundle()
                showSession(args, SHOW_FLAGS_TRIGGER)
            } catch (t: Throwable) {
                Log.w(TAG, "showSession 失败: ${t.message}")
            }
            mainHandler.postDelayed(this, TRIGGER_DELAY_MS)
        }
    }

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "数字助理服务已就绪")
        mainHandler.post(triggerRunnable)
    }

    override fun onShutdown() {
        super.onShutdown()
        mainHandler.removeCallbacks(triggerRunnable)
        Log.i(TAG, "数字助理服务已关闭")
    }
}
