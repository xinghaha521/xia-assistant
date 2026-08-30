package com.aifriend.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log

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
        val structure: AssistStructure? = try { state.assistStructure } catch (t: Throwable) { null }
        if (structure != null) {
            try {
                val xml = AssistNodeDumper.dump(structure)
                if (!xml.isNullOrEmpty()) {
                    NodeFileWriter.write(xml)
                    Log.i(TAG, "AssistStructure 写入成功 size=${xml.length}")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "AssistStructure 处理失败: ${t.message}")
            }
        } else {
            Log.w(TAG, "AssistState 缺少 AssistStructure")
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
