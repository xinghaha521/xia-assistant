package com.aifriend.assistant

import android.app.Application
import android.util.Log

/**
 * 应用 Application
 * 用于全局初始化：日志开关、调试状态共享
 */
class XiaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "XiaApp 启动")
    }

    companion object {
        private const val TAG = "XiaApp"

        @Volatile
        lateinit var instance: XiaApp
            private set
    }
}