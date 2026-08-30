package com.aifriend.assistant

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log

/**
 * 仿 vis MyVoiceInteractionSession
 * - onHandleAssist 转发到 VoiceCommandService.onHandleAssist
 * - scheduleFinish(1500ms) 仿 vis FINISH_FALLBACK_DELAY_MS
 */
class MyVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
    companion object {
        private const val TAG = "MySession"
        private const val FINISH_DELAY_MS = 1500L
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val finishRunnable = Runnable {
        try { finish() } catch (t: Throwable) { Log.w(TAG, "finish 失败: ${t.message}") }
    }

    private fun scheduleFinish(delayMs: Long) {
        mainHandler.removeCallbacks(finishRunnable)
        mainHandler.postDelayed(finishRunnable, delayMs)
    }

    override fun onHandleAssist(state: VoiceInteractionSession.AssistState) {
        super.onHandleAssist(state)
        try {
            // 转发给 VoiceCommandService，由它负责：
            // 1) 填充 AssistStructureCache
            // 2) 写文件兑底
            // 3) 通知 ProxyService callback
            VoiceCommandService.instance?.onHandleAssist(state)
        } catch (t: Throwable) {
            Log.e(TAG, "onHandleAssist 失败: ${t.message}", t)
        }
        scheduleFinish(FINISH_DELAY_MS)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        scheduleFinish(FINISH_DELAY_MS)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(finishRunnable)
        super.onDestroy()
    }
}