package com.aifriend.assistant

import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * 文件共享输出 - 输出标准 UIAutomator XML（与 uiautomator dump 输出兼容）
 *
 * 与 EC 主程序通过 /sdcard/xiaoa/ 共享通信文件。
 * - 文件名：screen.xml（沿用旧名，文件兑底路径不变）
 * - 内容：标准 UIAutomator XML（<?xml ...?><hierarchy><node .../></hierarchy>）
 * - 原子写：先写 .tmp，再 rename
 * - 元数据：meta.json（v / ts / len / nodes / snap）
 */
object NodeFileWriter {
    private const val TAG = "NodeFileWriter"
    private const val DIR = "/sdcard/xiaoa/"
    private const val XML_NAME = "screen.xml"
    private const val META_NAME = "meta.json"

    @Volatile
    private var version = 0L

    /**
     * 新接口：写入 UIAutomator 标准 XML
     */
    fun writeSync(nodes: List<UiObjectLite>, snapshotVersion: Int): Boolean = try {
        val xml = buildUiAutomatorXml(nodes)
        val dir = File(DIR)
        if (!dir.exists()) dir.mkdirs()

        // 原子写 XML
        val tmpXml = File(dir, "$XML_NAME.tmp")
        tmpXml.writeText(xml, Charsets.UTF_8)
        val xmlFile = File(dir, XML_NAME)
        xmlFile.delete()
        tmpXml.renameTo(xmlFile)

        version++
        val meta = JSONObject().apply {
            put("v", version)
            put("ts", System.currentTimeMillis())
            put("len", xml.length)
            put("nodes", nodes.size)
            put("snap", snapshotVersion)
        }.toString()
        writeMeta(meta)
        Log.i(TAG, "UIAutomator XML 写入成功 v=$version nodes=${nodes.size} bytes=${xml.length}")
        true
    } catch (t: Throwable) {
        Log.w(TAG, "UIAutomator XML 写入失败: ${t.message}")
        false
    }

    /**
     * 构造 UIAutomator 标准 XML
     * 格式（兼容 uiautomator dump 输出）：
     * <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
     * <hierarchy rotation="0">
     *   <node bounds="[l,t][r,b]" class="..." text="..." resource-id="..."
     *          content-desc="..." clickable="true/false" .../>
     *   ...
     * </hierarchy>
     */
    private fun buildUiAutomatorXml(nodes: List<UiObjectLite>): String {
        val sb = StringBuilder(4096)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<hierarchy rotation=\"0\">\n")
        for (n in nodes) {
            sb.append("  <node")
            appendAttr(sb, "bounds", "[${n.left},${n.top}][${n.right},${n.bottom}]")
            appendAttr(sb, "class", n.className)
            if (n.text.isNotEmpty()) appendAttr(sb, "text", n.text)
            if (n.desc.isNotEmpty()) appendAttr(sb, "content-desc", n.desc)
            if (n.resourceId.isNotEmpty()) appendAttr(sb, "resource-id", n.resourceId)
            if (n.packageName.isNotEmpty()) appendAttr(sb, "package", n.packageName)
            appendAttr(sb, "clickable", n.clickable.toString())
            appendAttr(sb, "focusable", n.focusable.toString())
            appendAttr(sb, "enabled", "true")
            sb.append("/>\n")
        }
        sb.append("</hierarchy>")
        return sb.toString()
    }

    private fun appendAttr(sb: StringBuilder, name: String, value: String) {
        sb.append(' ').append(name).append("=\"").append(escapeXml(value)).append('"')
    }

    private fun escapeXml(s: String): String {
        if (s.indexOf('&') < 0 && s.indexOf('<') < 0 &&
            s.indexOf('>') < 0 && s.indexOf('"') < 0 && s.indexOf('\'') < 0) {
            return s
        }
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun writeMeta(meta: String) {
        val dir = File(DIR)
        val tmpMeta = File(dir, "$META_NAME.tmp")
        tmpMeta.writeText(meta)
        val metaFile = File(dir, META_NAME)
        metaFile.delete()
        tmpMeta.renameTo(metaFile)
    }
}