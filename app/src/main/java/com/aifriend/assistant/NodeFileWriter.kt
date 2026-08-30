package com.aifriend.assistant

import android.util.Log
import java.io.File

/**
 * 文件共享输出
 *
 * 与 EC 主程序通过 /sdcard/xiaoa/ 共享通信文件。
 */
object NodeFileWriter {
    private const val TAG = "NodeFileWriter"
    private const val DIR = "/sdcard/xiaoa/"
    private const val XML_NAME = "screen.xml"
    private const val META_NAME = "meta.json"

    @Volatile
    private var version = 0L

    fun write(xml: String): Boolean {
        return try {
            val dir = File(DIR)
            if (!dir.exists()) dir.mkdirs()

            val tmpXml = File(dir, "$XML_NAME.tmp")
            tmpXml.writeText(xml)
            val xmlFile = File(dir, XML_NAME)
            xmlFile.delete()
            tmpXml.renameTo(xmlFile)

            version++
            val meta = "{\"v\":$version,\"ts\":${System.currentTimeMillis()},\"len\":${xml.length}}"
            val tmpMeta = File(dir, "$META_NAME.tmp")
            tmpMeta.writeText(meta)
            val metaFile = File(dir, META_NAME)
            metaFile.delete()
            tmpMeta.renameTo(metaFile)

            Log.i(TAG, "文件写入成功 v=$version len=${xml.length}")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "文件写入失败: ${t.message}")
            false
        }
    }
}