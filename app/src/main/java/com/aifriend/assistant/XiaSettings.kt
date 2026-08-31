package com.aifriend.assistant

import android.content.Context

/**
 * 小A服务运行设置（v0.8.0+）
 *
 * 采集模式：
 * - MODE_DIGITAL: 数字助理模式（默认，通过 VoiceInteractionService）
 * - MODE_ACCESSIBILITY: 无障碍模式（备用，通过 AccessibilityService）
 */
object XiaSettings {
    private const val PREFS = "xia_settings"
    private const val KEY_MODE = "collect_mode"

    const val MODE_DIGITAL = 0
    const val MODE_ACCESSIBILITY = 1

    fun getMode(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_MODE, MODE_DIGITAL)

    fun setMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MODE, mode)
            .apply()
    }

    fun isAccessibilityMode(context: Context): Boolean =
        getMode(context) == MODE_ACCESSIBILITY
}
