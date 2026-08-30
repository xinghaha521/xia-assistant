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

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "数字助理服务已就绪")
    }

    override fun onHandleAssist(
        data: Bundle?,
        structure: AssistStructure?,
        content: AssistContent?
    ) {
        super.onHandleAssist(data, structure, content)
        if (structure == null) return
        try {
            val xml = AssistNodeDumper.dump(structure)
            if (!xml.isNullOrEmpty()) {
                NodeFileWriter.write(xml)
                Log.i(TAG, "AssistStructure 写入成功 size=${xml.length}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "AssistStructure 处理失败: ${t.message}")
        }
    }

    override fun onShutdown() {
        super.onShutdown()
        Log.i(TAG, "数字助理服务已关闭")
    }
}