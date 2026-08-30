package com.aifriend.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log

class MyVoiceInteractionSessionService : VoiceInteractionSessionService() {

    companion object {
        private const val TAG = "VisSessionService"
    }

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        Log.i(TAG, "创建数字助理会话")
        return MyVoiceInteractionSession(this)
    }
}