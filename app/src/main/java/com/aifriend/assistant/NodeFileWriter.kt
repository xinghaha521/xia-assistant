package com.aifriend.assistant

import android.util.Log
import java.io.File

/**
 * 文件共享输出
 *
 * 与【主程序】(EC) 通过 /sdcard/xiaoa/ 共享文件通信：
 * - screen.xml  当前屏幕节点 XML（原子写：tmp + rename，避免主程序读到半截）
 * - meta.json   版本号 v + 时间戳 ts + 长度 len，主程序据此判断新鲜度
 *
 * 写入依赖 WRITE_EXTERNAL_STORAGE（应用内运行时授权，Android 10- 直接生效，
 * Android 11 需 requestLegacyExternalStorage + 授权）。
 */
object NodeFileWriter {
    private const val TAG = "NodeFileWriter"
    private const val DIR = "/sdcard/xiaoa/"
    private const val XML_NAME = "screen.xml"
    private const val META_NAME = "meta.json"

    @Volatile
    private var version = 0L

    /**
     * 原子写入 XML 与元信息
     * @param xml 节点 XML 内容
     * @return 是否成功
     */
    fun write(xml: String): Boolean {
        return try {
            val dir = File(DIR)
            if (!dir.exists()) dir.mkdirs()

            // XML：tmp + rename 保证原子性
            val tmpXml = File(DIR + "screen.xml.tmp")
            tmpXml.writeText(xml)
            val xmlFile = File(DIR + XML_NAME)
            xmlFile.delete()
            tmpXml.renameTo(xmlFile)

            // meta：版本号自增，主程序据此感知新数据
            version++
            val meta = "{\"v\":$version,\"ts\":${System.currentTimeMillis()},\"len\":${xml.length}}"
            val tmpMeta = File(DIR + "meta.json.tmp")
            tmpMeta.writeText(meta)
            val metaFile = File(DIR + META_NAME)
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