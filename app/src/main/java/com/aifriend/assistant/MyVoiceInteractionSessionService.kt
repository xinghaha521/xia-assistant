package com.aifriend.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log

/**
 * 数字助理会话服务
 *
 * 系统绑定此服务以创建会话；本应用读取屏幕走无障碍服务（NodeService），
 * 会话仅保持最小实现，保证服务可正常绑定即可。
 */
class MyVoiceInteractionSessionService : VoiceInteractionSessionService() {

    companion object {
        private const val TAG = "VisSessionService"
    }

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        Log.i(TAG, "创建数字助理会话")
        return MyVoiceInteractionSession(this)
    }
}