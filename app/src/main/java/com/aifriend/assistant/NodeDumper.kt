package com.aifriend.assistant

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 节点 dump 工具
 *
 * 设计要点：
 * - Android 没有公开的 dumpXml() API，但可以通过 AccessibilityNodeInfo 树手写序列化
 * - 这里手写一个轻量序列化器，输出与 uiautomator 一致的 XML 格式
 * - 节点深度限制：避免卡死和内存爆炸
 * - 文本截断：避免单节点内容过大撑爆内存
 */
object NodeDumper {

    private const val TAG = "NodeDumper"
    private const val MAX_DEPTH = 50          // 最大递归深度
    private const val MAX_NODES = 5000        // 最大节点数
    private const val MAX_TEXT_LEN = 200      // 单个 text 属性最大长度
    private const val MAX_DESC_LEN = 200

    /**
     * 对外入口：dump 整棵树为 XML 字符串
     * 优先级：
     * 1. 反射调用 AccessibilityService.dump() （hidden API，uiautomator 同款）
     * 2. 走 rootInActiveWindow 序列化
     */
    fun dumpService(service: AccessibilityService): String? {
        // 方法1：反射调用隐藏 dump()
        val reflectionXml = tryDumpViaReflection(service)
        if (!reflectionXml.isNullOrBlank()) {
            Log.i(TAG, "反射 dump 成功, size=${reflectionXml.length}")
            return reflectionXml
        }

        // 方法2：rootInActiveWindow（兜底）
        val root = service.rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "rootInActiveWindow 为 null，反射也失败")
            return null
        }
        Log.d(TAG, "rootInActiveWindow: pkg=${root.packageName} class=${root.className} children=${root.childCount}")
        return dumpNode(root)
    }

    /**
     * 反射调用 AccessibilityService.dump() 隐藏方法
     * 这是 uiautomator dumpXml() 的实现路径
     * 该方法在某些 ROM（特别是 MIUI）下能绕过 rootInActiveWindow 的限制
     */
    private fun tryDumpViaReflection(service: AccessibilityService): String? {
        // 方案 1：调用 service 的 dump() 方法
        try {
            val method = service.javaClass.methods.firstOrNull { it.name == "dump" && it.parameterCount == 0 }
            if (method != null) {
                Log.d(TAG, "找到 dump 方法: $method")
                val fd = method.invoke(service) as java.io.FileDescriptor
                val pfd = android.os.ParcelFileDescriptor.dup(fd)
                val fis = java.io.FileInputStream(pfd.fileDescriptor)
                val result = fis.bufferedReader(Charsets.UTF_8).use { it.readText() }
                pfd.close()
                if (result.isNotBlank()) return result
            } else {
                Log.w(TAG, "service.dump() 方法不存在")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "反射 dump() 失败: ${t.message}")
        }

        // 方案 2：调用 AccessibilityNodeInfo 的 dumpStream 方法
        val root = service.rootInActiveWindow ?: return null
        return try {
            val writeToParcelMethod = AccessibilityNodeInfo::class.java.getMethod(
                "writeToParcel", android.os.Parcel::class.java, Int::class.java
            )
            // 不适用，方向反了

            // 用 Parcel.readXml 替代
            val parcel = android.os.Parcel.obtain()
            val flags = 0
            writeToParcelMethod.invoke(root, parcel, flags)

            val bytes = parcel.marshall()
            parcel.recycle()

            // 用 uiautomator 的 serializer
            // 这里退回到 walk 序列化
            dumpNode(root)
        } catch (t: Throwable) {
            Log.w(TAG, "反射 Parcel 方案失败: ${t.message}")
            null
        }
    }

    /**
     * dump 单个节点及其子树
     */
    fun dumpNode(root: AccessibilityNodeInfo): String {
        val sb = StringBuilder(4096)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<hierarchy rotation=\"0\">\n")
        val counter = intArrayOf(0)
        walk(root, sb, 0, counter)
        sb.append("</hierarchy>")
        return sb.toString()
    }

    /**
     * 递归遍历节点（深度优先）
     */
    private fun walk(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int, counter: IntArray) {
        if (node == null || depth > MAX_DEPTH || counter[0] > MAX_NODES) return
        counter[0]++

        val className = node.className?.toString() ?: "android.view.View"
        appendIndent(sb, depth)
        sb.append('<').append(escapeAttr(className))
        sb.append(getAttrsString(node))
        sb.append(">\n")

        // 递归子节点
        val childCount = node.childCount
        for (i in 0 until childCount) {
            walk(node.getChild(i), sb, depth + 1, counter)
        }

        // 闭合标签
        appendIndent(sb, depth)
        sb.append("</").append(escapeAttr(className)).append(">\n")
    }

    /**
     * 构造属性字符串
     */
    private fun getAttrsString(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        sb.append(" bounds=\"[")
        sb.append(bounds.left).append(',').append(bounds.top).append("][")
        sb.append(bounds.right).append(',').append(bounds.bottom).append("]\"")

        node.text?.let {
            sb.append(" text=\"").append(escapeAttr(truncate(it.toString(), MAX_TEXT_LEN))).append('"')
        }
        node.contentDescription?.let {
            sb.append(" content-desc=\"").append(escapeAttr(truncate(it.toString(), MAX_DESC_LEN))).append('"')
        }

        // resource-id
        // 注意：getUniqueId() 是 API 33+ 新增，Android 11 及以下调用会 NoSuchMethodError，
        // 因此统一使用 viewIdResourceName（API 18+ 稳定可用）
        var resId: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            node.uniqueId?.let { resId = it }
        }
        if (resId.isNullOrEmpty()) {
            @Suppress("DEPRECATION")
            resId = node.viewIdResourceName
        }
        val finalResId = resId
        if (!finalResId.isNullOrEmpty()) {
            sb.append(" resource-id=\"").append(escapeAttr(finalResId)).append('"')
        }

        node.packageName?.let {
            sb.append(" package=\"").append(escapeAttr(it.toString())).append('"')
        }
        if (node.isClickable) sb.append(" clickable=\"true\"")
        if (node.isLongClickable) sb.append(" long-clickable=\"true\"")
        if (node.isFocusable) sb.append(" focusable=\"true\"")
        if (node.isScrollable) sb.append(" scrollable=\"true\"")
        if (node.isCheckable) sb.append(" checkable=\"true\"")
        if (node.isChecked) sb.append(" checked=\"true\"")
        if (node.isEnabled) sb.append(" enabled=\"true\"")
        if (node.isSelected) sb.append(" selected=\"true\"")
        sb.append(" index=\"0\"")

        return sb.toString()
    }

    private fun appendIndent(sb: StringBuilder, depth: Int) {
        for (i in 0 until depth) sb.append("    ")
    }

    /**
     * 转义 XML 特殊字符
     */
    private fun escapeAttr(s: String): String {
        if (s.isEmpty()) return ""
        val sb = StringBuilder(s.length + 16)
        for (c in s) {
            when (c) {
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '&' -> sb.append("&amp;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                '\n' -> sb.append("&#10;")
                '\r' -> sb.append("&#13;")
                '\t' -> sb.append("&#9;")
                else -> {
                    if (c.code < 0x20) {
                        sb.append("&#").append(c.code).append(';')
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }

    /**
     * 截断过长文本
     */
    private fun truncate(s: String, max: Int): String {
        return if (s.length <= max) s else s.substring(0, max) + "..."
    }
}