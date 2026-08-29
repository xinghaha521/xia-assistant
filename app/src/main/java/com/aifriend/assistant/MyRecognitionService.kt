package com.aifriend.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * 语音识别服务（最小实现）
 *
 * 本应用不依赖语音输入，收到识别请求直接返回错误；
 * 保留此服务是为了满足 voice_interaction_service.xml 中的 recognitionService 引用，
 * 保证数字助理身份完整、可被系统识别。
 */
class MyRecognitionService : RecognitionService() {

    companion object {
        private const val TAG = "MyRecognitionService"
    }

    override fun onStartListening(intent: Intent?, listener: Callback?) {
        Log.i(TAG, "收到识别请求，本应用不支持语音输入")
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback?) {
        Log.i(TAG, "识别已取消")
    }

    override fun onStopListening(listener: Callback?) {
        Log.i(TAG, "停止识别")
    }
}