package com.aifriend.assistant

import android.app.assist.AssistStructure
import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log

/**
 * 数字助理会话（最小实现）
 *
 * 收到 assist 请求后直接结束会话；读取屏幕数据走无障碍服务，
 * 不在此处处理 AssistStructure。
 */
class MyVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    companion object {
        private const val TAG = "MyVisSession"
    }

    override fun onHandleAssist(args: Bundle?, structure: AssistStructure?) {
        super.onHandleAssist(args, structure)
        Log.i(TAG, "收到 assist 请求（读取屏幕改走无障碍服务）")
        finish()
    }
}