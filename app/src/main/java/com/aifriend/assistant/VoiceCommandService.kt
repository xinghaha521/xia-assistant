package com.aifriend.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * 数字助理服务（仿 vis VoiceCommandService）
 *
 * 关键修复：
 * - onReady 不再启动死循环（之前 commit 66a404d 在 onReady 里 3 秒循环
 *   调 showSession，导致 MIUI 12.5 屏幕卡死）
 * - triggerNewSession 只在 EC 显式调用时才拉 Session
 *
 * 数据流：
 *   EC -> ProxyService.triggerAssistSession()
 *      -> VoiceCommandService.triggerNewSession()
 *      -> showSession(Bundle, SHOW_FLAGS_TRIGGER=9)
 *      -> system_server 拉起 MyVoiceInteractionSession
 *      -> session.onHandleAssist(AssistState)
 *      -> session 调 VoiceCommandService.onHandleAssist(state)
 *      -> AssistStructureCache.update(state.assistStructure)
 *      -> 写文件兑底 /sdcard/xiaoa/screen.xml + meta.json
 *      -> ProxyService.notifySnapshotUpdated(version)
 *      -> EC IXiaCallback.onSnapshotUpdated(version)
 */
class VoiceCommandService : VoiceInteractionService() {

    companion object {
        private const val TAG = "VoiceCommandService"
        private const val SHOW_FLAGS_TRIGGER = 9
        private const val FINISH_DELAY_MS = 1500L

        @Volatile
        var instance: VoiceCommandService? = null
    }

    override fun onReady() {
        super.onReady()
        instance = this
        Log.i(TAG, "数字助理服务已就绪（事件驱动，不再轮询）")
    }

    override fun onShutdown() {
        instance = null
        Log.i(TAG, "数字助理服务已关闭")
        super.onShutdown()
    }

    /**
     * EC 通过 ProxyService 显式触发
     */
    fun triggerNewSession() {
        try {
            Log.i(TAG, "triggerNewSession: 拉起 VoiceSession")
            val args = Bundle()
            showSession(args, SHOW_FLAGS_TRIGGER)
        } catch (t: Throwable) {
            Log.e(TAG, "showSession 失败: ${t.message}", t)
        }
    }

    /**
     * 由 MyVoiceInteractionSession.onHandleAssist 转发
     */
    fun onHandleAssist(state: android.service.voice.VoiceInteractionSession.AssistState) {
        try {
            val structure = state.assistStructure
            Log.i(TAG, "onHandleAssist: windowCount=${structure?.windowNodeCount}")
            AssistStructureCache.update(structure)

            // 写文件兑底（EC 端读这个文件触发 trigger/伪选择器）
            val nodes = AssistStructureCache.getSnapshot()
            val version = AssistStructureCache.getVersion()
            NodeFileWriter.writeSync(this, nodes, version)

            // 通知 ProxyService 反向回调 EC
            VoiceCommandProxyService.notifySnapshotUpdatedStatic(version)
        } catch (t: Throwable) {
            Log.e(TAG, "onHandleAssist 失败: ${t.message}", t)
        }
    }
}