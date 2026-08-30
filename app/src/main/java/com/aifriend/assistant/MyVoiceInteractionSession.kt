package com.aifriend.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log

class MyVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
    override fun onHandleAssist(data: Bundle?, structure: AssistStructure?, content: AssistContent?) {
        super.onHandleAssist(data, structure, content)
        if (structure == null) return
        try {
            val xml = AssistNodeDumper.dump(structure)
            if (!xml.isNullOrEmpty()) {
                NodeFileWriter.write(xml)
                Log.i("MyVoiceInteractionSession", "AssistStructure 写入成功 size=${xml.length}")
            }
        } catch (t: Throwable) {
            Log.w("MyVoiceInteractionSession", "AssistStructure 处理失败: ${t.message}")
        }
    }
}