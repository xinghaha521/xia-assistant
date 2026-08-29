package com.aifriend.assistant

import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * 数字助理核心服务
 *
 * 声明该服务后，本应用会出现在 MIUI「数字助理」列表中，
 * 用户可将其设为系统数字助理，从而获得后台保活与读屏能力。
 *
 * 本应用不依赖语音交互，仅需保证服务可被系统绑定即可。
 */
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