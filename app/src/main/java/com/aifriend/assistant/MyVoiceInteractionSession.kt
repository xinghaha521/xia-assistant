package com.aifriend.assistant

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.WindowManager

/**
 * 仿 vis MyVoiceInteractionSession
 *
 * v0.8.2 防闪屏关键修复（参考 vis 反编译）：
 *   onShow 中 addFlags(56) → NOT_FOCUSABLE + NOT_TOUCHABLE + NOT_TOUCH_MODAL
 *   → session 窗口不可见/不可交互，系统不渲染 AssistPreviewPanel 浮层
 *   onHandleAssist 中 scheduleFinish(10) → 10ms 立即 finish（vis FINISH_AFTER_ASSIST_DELAY_MS）
 */
class MyVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
    companion object {
        private const val TAG = "MySession"
        private const val FINISH_FALLBACK_DELAY_MS = 1500L  // fallback 保底
        private const val FINISH_AFTER_ASSIST_DELAY_MS = 10L  // vis: 数据到手 10ms 即关
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
        // vis: 数据到手 10ms 立即 finish，窗口几乎不存留
        scheduleFinish(FINISH_AFTER_ASSIST_DELAY_MS)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        // 防闪屏关键：参考 vis，让 session 窗口不可见/不可交互
        // 56 = FLAG_NOT_FOCUSABLE(8) + FLAG_NOT_TOUCHABLE(16) + FLAG_NOT_TOUCH_MODAL(32)
        try {
            window?.window?.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            )
        } catch (t: Throwable) {
            Log.w(TAG, "addFlags 防闪屏失败: ${t.message}")
        }
        // fallback 保底：1500ms 后确保 finish（正常情况 onHandleAssist 后 10ms 已关）
        scheduleFinish(FINISH_FALLBACK_DELAY_MS)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(finishRunnable)
        super.onDestroy()
    }
}