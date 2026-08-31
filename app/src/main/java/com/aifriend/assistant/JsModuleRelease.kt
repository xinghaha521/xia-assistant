package com.aifriend.assistant

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * JS 模块释放器
 *
 * 把 APK assets/js/ 下的 JS 模块复制到 /sdcard/js/
 * 让 EC 端 require() 加载，免去手动 adb push
 *
 * 为什么不用 /sdcard/Android/data/<pkg>/files/js/（scoped storage）？
 *   Android 11+ scoped storage 强制 owner-only 访问
 *   EC IDE 运行在 com.gibb.easyclick 用户下，**无法读取** com.aifriend.assistant 专属目录
 *   即使 chmod 644 也无效（内核层强制隔离）
 *
 * 设计：自愈式
 *   - 文件不存在 → 复制
 *   - 文件存在 → 不动（保留 MT管理器 手动覆盖的版本）
 *   - 文件被删 → 下次 trigger 时自动补回
 *
 * 调用时机：
 *   - MainActivity.onCreate()    首次启动兜底
 *   - VoiceCommandService.onHandleAssist()   每次 trigger 自愈
 */
object JsModuleRelease {
    private const val TAG = "JsModuleRelease"
    private const val ASSETS_DIR = "js"
    private const val TARGET_DIR = "/sdcard/js"
    private val MODULES = arrayOf("xiaClient.js", "selectors.js", "bleHid.js")

    fun ensureReleased(context: Context): Int {
        val targetDir = File(TARGET_DIR)
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            Log.e(TAG, "创建目标目录失败: $targetDir")
            return 0
        }

        var count = 0
        for (name in MODULES) {
            val target = File(targetDir, name)
            if (target.exists()) {
                Log.d(TAG, "已存在: $name (跳过)")
                continue
            }
            try {
                context.assets.open("$ASSETS_DIR/$name").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                // 设置世界可读，让 EC IDE（不同用户）能读取
                target.setReadable(true, false)
                target.setWritable(true, false)
                Log.i(TAG, "释放: $name → ${target.absolutePath}")
                count++
            } catch (e: Exception) {
                Log.e(TAG, "释放失败: $name", e)
            }
        }
        return count
    }
}
