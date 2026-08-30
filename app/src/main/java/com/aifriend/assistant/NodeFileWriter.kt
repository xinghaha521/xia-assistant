package com.aifriend.assistant

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 文件共享输出
 *
 * 与 EC 主程序通过 /sdcard/xiaoa/ 共享通信文件。
 * - 原子写：先写 .tmp，再 rename
 * - 协议：screen.xml + meta.json（v / ts / len）
 */
object NodeFileWriter {
    private const val TAG = "NodeFileWriter"
    private const val DIR = "/sdcard/xiaoa/"
    private const val XML_NAME = "screen.xml"
    private const val META_NAME = "meta.json"

    @Volatile
    private var version = 0L

    /**
     * 兼容旧接口：写入原始 XML 字符串
     */
    fun write(xml: String): Boolean = try {
        val dir = File(DIR)
        if (!dir.exists()) dir.mkdirs()
        val tmpXml = File(dir, "$XML_NAME.tmp")
        tmpXml.writeText(xml)
        val xmlFile = File(dir, XML_NAME)
        xmlFile.delete()
        tmpXml.renameTo(xmlFile)

        version++
        val meta = JSONObject().apply {
            put("v", version)
            put("ts", System.currentTimeMillis())
            put("len", xml.length)
            put("nodes", 0)
        }.toString()
        writeMeta(meta)
        Log.i(TAG, "XML 写入成功 v=$version len=${xml.length}")
        true
    } catch (t: Throwable) {
        Log.w(TAG, "XML 写入失败: ${t.message}")
        false
    }

    /**
     * 新接口：写入 UiObjectLite 列表（由 AssistStructureCache 提供）
     */
    fun writeSync(nodes: List<UiObjectLite>, snapshotVersion: Int): Boolean = try {
        val json = JSONObject()
        val arr = JSONArray()
        for (n in nodes) {
            val obj = JSONObject()
            obj.put("text", n.text)
            obj.put("desc", n.desc)
            obj.put("id", n.resourceId)
            obj.put("cls", n.className)
            obj.put("pkg", n.packageName)
            obj.put("l", n.left)
            obj.put("t", n.top)
            obj.put("r", n.right)
            obj.put("b", n.bottom)
            obj.put("clickable", n.clickable)
            obj.put("focusable", n.focusable)
            obj.put("visible", n.visibleToUser)
            arr.put(obj)
        }
        json.put("nodes", arr)

        val dir = File(DIR)
        if (!dir.exists()) dir.mkdirs()
        val xmlFile = File(dir, XML_NAME)
        val jsonStr = json.toString()
        val tmpXml = File(dir, "$XML_NAME.tmp")
        tmpXml.writeText(jsonStr)
        xmlFile.delete()
        tmpXml.renameTo(xmlFile)

        version++
        val meta = JSONObject().apply {
            put("v", version)
            put("ts", System.currentTimeMillis())
            put("len", jsonStr.length)
            put("nodes", nodes.size)
            put("snap", snapshotVersion)
        }.toString()
        writeMeta(meta)
        Log.i(TAG, "节点写入成功 v=$version nodes=${nodes.size}")
        true
    } catch (t: Throwable) {
        Log.w(TAG, "节点写入失败: ${t.message}")
        false
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