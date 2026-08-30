package com.aifriend.assistant

import android.app.assist.AssistStructure
import android.graphics.Rect
import android.view.View

object AssistNodeDumper {
    private const val MAX_DEPTH = 50
    private const val MAX_NODES = 5000

    fun dump(structure: AssistStructure): String {
        val sb = StringBuilder(8192)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<hierarchy rotation=\"0\">\n")
        var count = 0
        for (i in 0 until structure.windowNodeCount) {
            val window = structure.getWindowNodeAt(i)
            count = walk(window.rootViewNode, sb, 0, count)
        }
        sb.append("</hierarchy>")
        return sb.toString()
    }

    private fun walk(node: AssistStructure.ViewNode?, sb: StringBuilder, depth: Int, count: Int): Int {
        if (node == null || depth > MAX_DEPTH || count >= MAX_NODES) return count
        val nextCount = count + 1
        val className = node.className?.toString() ?: "android.view.View"
        val bounds = Rect(node.left, node.top, node.left + node.width, node.top + node.height)
        repeat(depth) { sb.append("    ") }
        sb.append("<node class=\"").append(escape(className)).append("\"")
        sb.append(" bounds=\"[").append(bounds.left).append(',').append(bounds.top)
            .append("][").append(bounds.right).append(',').append(bounds.bottom).append("]\"")
        appendAttr(sb, "text", node.text?.toString())
        appendAttr(sb, "content-desc", node.contentDescription?.toString())
        appendAttr(sb, "resource-id", node.idEntry)
        appendAttr(sb, "package", node.idPackage)
        if (node.isClickable) sb.append(" clickable=\"true\"")
        if (node.isFocusable) sb.append(" focusable=\"true\"")
        if (node.isEnabled) sb.append(" enabled=\"true\"")
        sb.append(">\n")
        var total = nextCount
        for (i in 0 until node.childCount) total = walk(node.getChildAt(i), sb, depth + 1, total)
        repeat(depth) { sb.append("    ") }
        sb.append("</node>\n")
        return total
    }

    private fun appendAttr(sb: StringBuilder, name: String, value: String?) {
        if (!value.isNullOrEmpty()) sb.append(' ').append(name).append("=\"").append(escape(value)).append('"')
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;").replace("\"", "&quot;")
        .replace("<", "&lt;").replace(">", "&gt;")
        .replace("'", "&apos;")
}