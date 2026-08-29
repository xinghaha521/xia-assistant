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
     */
    fun dumpService(service: AccessibilityService): String? {
        val root = service.rootInActiveWindow ?: return null
        return dumpNode(root)
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

        // resource-id（API >= 28 优先用 uniqueId，老版本用 viewIdResourceName）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            node.uniqueId?.let {
                sb.append(" resource-id=\"").append(escapeAttr(it)).append('"')
            }
        } else {
            @Suppress("DEPRECATION")
            node.viewIdResourceName?.let {
                sb.append(" resource-id=\"").append(escapeAttr(it)).append('"')
            }
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