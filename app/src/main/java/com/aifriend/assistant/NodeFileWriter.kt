package com.aifriend.assistant

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * 文件共享输出 - 输出标准 UIAutomator XML
 *
 * 与 EC 主程序通过应用专属外部存储目录共享通信文件：
 *   /sdcard/Android/data/com.aifriend.assistant/files/xiaoa/
 *   ├─ screen.xml (UIAutomator XML)
 *   └─ meta.json  (v / ts / len / nodes / snap)
 *
 * 优势：
 * - 无需 WRITE_EXTERNAL_STORAGE 运行时权限（应用专属目录）
 * - 与 uiautomator dump 输出格式完全兼容
 * - 原子写：先写 .tmp，再 rename
 */
object NodeFileWriter {
    private const val TAG = "NodeFileWriter"
    private const val DIR_NAME = "xiaoa"
    private const val XML_NAME = "screen.xml"
    private const val META_NAME = "meta.json"

    @Volatile
    private var version = 0L

    @Volatile
    private var dirCache: String? = null

    /**
     * 解析并缓存目录路径（应用专属，无需权限）
     */
    fun resolveDir(context: Context): String {
        dirCache?.let { return it }
        val ext = context.getExternalFilesDir(null)
        val path = if (ext != null) {
            File(ext, DIR_NAME).apply { if (!exists()) mkdirs() }.absolutePath + "/"
        } else {
            // 兜底：用 context.filesDir（内部存储）
            File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }.absolutePath + "/"
        }
        dirCache = path
        Log.i(TAG, "解析目录: $path")
        return path
    }

    /**
     * 写入 UIAutomator XML（应用专属目录，无需权限）
     */
    fun writeSync(context: Context, nodes: List<UiObjectLite>, snapshotVersion: Int): Boolean = try {
        val xml = buildUiAutomatorXml(nodes)
        val dir = resolveDir(context)
        val dirFile = File(dir)
        if (!dirFile.exists()) dirFile.mkdirs()

        // 原子写 XML
        val tmpXml = File(dirFile, "$XML_NAME.tmp")
        tmpXml.writeText(xml, Charsets.UTF_8)
        val xmlFile = File(dirFile, XML_NAME)
        if (xmlFile.exists()) xmlFile.delete()
        if (!tmpXml.renameTo(xmlFile)) {
            Log.w(TAG, "rename tmp -> xml 失败，尝试直接写")
            tmpXml.copyTo(xmlFile, overwrite = true)
            tmpXml.delete()
        }

        version++
        val meta = JSONObject().apply {
            put("v", version)
            put("ts", System.currentTimeMillis())
            put("len", xml.length)
            put("nodes", nodes.size)
            put("snap", snapshotVersion)
            put("path", dir)
        }.toString()
        writeMeta(dirFile, meta)
        Log.i(TAG, "UIAutomator XML 写入成功 v=$version nodes=${nodes.size} bytes=${xml.length} dir=$dir")
        true
    } catch (t: Throwable) {
        Log.w(TAG, "UIAutomator XML 写入失败: ${t.message}", t)
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

    private fun writeMeta(dirFile: File, meta: String) {
        val tmpMeta = File(dirFile, "$META_NAME.tmp")
        tmpMeta.writeText(meta)
        val metaFile = File(dirFile, META_NAME)
        if (metaFile.exists()) metaFile.delete()
        if (!tmpMeta.renameTo(metaFile)) {
            tmpMeta.copyTo(metaFile, overwrite = true)
            tmpMeta.delete()
        }
    }
}