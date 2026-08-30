package com.aifriend.assistant

import android.service.voice.VoiceInteractionService
import android.util.Log

class VoiceCommandService : VoiceInteractionService() {

    companion object {
        private const val TAG = "VoiceCommandService"
    }

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "数字助理服务已就绪")
    }

    override fun onShutdown() {
        super.onShutdown()
        Log.i(TAG, "数字助理服务已关闭")
    }
}