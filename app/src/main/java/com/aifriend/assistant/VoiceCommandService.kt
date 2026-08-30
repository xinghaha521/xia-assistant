package com.aifriend.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.util.Log

class VoiceCommandService : VoiceInteractionService() {

    companion object {
        private const val TAG = "VoiceCommandService"
    }

    private val assistHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val assistRunnable = object : Runnable {
        override fun run() {
            try {
                showSession(Bundle(), 0)
            } catch (t: Throwable) {
                Log.w(TAG, "showSession 失败: ${t.message}")
            }
            assistHandler.postDelayed(this, 3000L)
        }
    }

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "数字助理服务已就绪")
        assistHandler.post(assistRunnable)
    }

    override fun onShutdown() {
        super.onShutdown()
        assistHandler.removeCallbacks(assistRunnable)
        Log.i(TAG, "数字助理服务已关闭")
    }
}