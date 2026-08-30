package com.aifriend.assistant

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteCallbackList
import android.util.Log

/**
 * 对外暴露的 Binder 接口（仿 vis VoiceCommandProxyService）
 */
class VoiceCommandProxyService : Service() {

    companion object {
        private const val TAG = "VoiceCommandProxyService"

        @Volatile
        private var instance: VoiceCommandProxyService? = null

        /**
         * 供 VoiceCommandService.onHandleAssist 调用的静态入口
         */
        fun notifySnapshotUpdatedStatic(version: Int) {
            val ins = instance
            if (ins == null) {
                Log.w(TAG, "ProxyService 未启动，跳过 callback 通知")
                return
            }
            ins.notifySnapshotUpdated(version)
        }
    }

    private val callbacks = RemoteCallbackList<IXiaCallback>()

    private val binder = object : IXiaService.Stub() {

        override fun triggerAssistSession() {
            Log.i(TAG, "EC 请求触发数字助理 session")
            val svc = VoiceCommandService.instance
            if (svc != null) {
                svc.triggerNewSession()
            } else {
                Log.w(TAG, "VoiceCommandService 尚未就绪（onReady 未触发）")
            }
        }

        override fun getCurrentSnapshot(): List<UiObjectLite> {
            val snap = AssistStructureCache.getSnapshot()
            Log.d(TAG, "EC 请求快照 version=${AssistStructureCache.getVersion()} size=${snap.size}")
            return snap
        }

        override fun registerCallback(callback: IXiaCallback) {
            if (callback != null) callbacks.register(callback)
        }

        override fun unregisterCallback(callback: IXiaCallback) {
            if (callback != null) callbacks.unregister(callback)
        }

        override fun getSnapshotVersion(): Int = AssistStructureCache.getVersion()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "ProxyService 启动")
    }

    override fun onDestroy() {
        instance = null
        callbacks.kill()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun notifySnapshotUpdated(version: Int) {
        val n = callbacks.beginBroadcast()
        try {
            for (i in 0 until n) {
                try {
                    callbacks.getBroadcastItem(i).onSnapshotUpdated(version)
                } catch (t: Throwable) {
                    Log.w(TAG, "callback 回调失败: ${t.message}")
                }
            }
        } finally {
            callbacks.finishBroadcast()
        }
    }
}