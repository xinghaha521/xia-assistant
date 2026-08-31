package com.aifriend.assistant

import android.app.assist.AssistStructure
import android.util.Log

/**
 * AssistStructure 内存缓存
 * 仿 vis AssistStructureCache，存 UiObjectLite 列表
 */
object AssistStructureCache {

    private const val TAG = "AssistStructureCache"

    @Volatile
    private var snapshot: List<UiObjectLite> = emptyList()

    @Volatile
    private var version: Int = 0

    @Synchronized
    fun update(structure: AssistStructure?) {
        try {
            if (structure == null) {
                Log.w(TAG, "AssistStructure 为空，跳过更新")
                return
            }
            val windowCount = structure.windowNodeCount
            Log.d(TAG, "更新快照 windowCount=$windowCount")
            val newSnapshot = ArrayList<UiObjectLite>()
            for (w in 0 until windowCount) {
                val window = structure.getWindowNodeAt(w)
                val pkg = window.title?.toString() ?: ""
                val root: AssistStructure.ViewNode? = try { window.rootViewNode } catch (t: Throwable) { null }
                if (root != null) {
                    val winLeft = try { window.left } catch (t: Throwable) { 0 }
                    val winTop = try { window.top } catch (t: Throwable) { 0 }
                    val winWidth = try { window.width } catch (t: Throwable) { 0 }
                    val winHeight = try { window.height } catch (t: Throwable) { 0 }
                    Log.d(TAG, "DEBUG Window #$w pkg=$pkg winLeft=$winLeft winTop=$winTop winWidth=$winWidth winHeight=$winHeight rootLeft=${root.left} rootTop=${root.top}")
                    dumpNode(root, pkg, newSnapshot, 0, winLeft, winTop)
                }
            }
            snapshot = newSnapshot
            version++
            Log.d(TAG, "快照已更新 version=$version nodes=${newSnapshot.size}")
        } catch (t: Throwable) {
            Log.e(TAG, "更新失败: ${t.message}", t)
        }
    }

    private fun dumpNode(
        node: AssistStructure.ViewNode,
        pkgFallback: String,
        out: ArrayList<UiObjectLite>,
        depth: Int = 0,
        winLeft: Int = 0,
        winTop: Int = 0
    ) {
        try {
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            val rid = node.idEntry ?: ""
            val cls = node.className ?: ""
            val pkg = node.idPackage ?: pkgFallback
            val indent = "  ".repeat(depth)
            // 调试：打印所有有意义的节点
            if (text.isNotEmpty() || desc.isNotEmpty() || rid.isNotEmpty() || cls.isNotEmpty()) {
                Log.d(TAG, "${indent}DUMP cls=$cls rid=$rid rawL=${node.left} rawT=${node.top} w=${node.width} h=${node.height} winL=$winLeft winT=$winTop")
                out.add(
                    UiObjectLite(
                        text = text,
                        desc = desc,
                        resourceId = rid,
                        className = cls,
                        packageName = pkg,
                        left = winLeft + node.left,
                        top = winTop + node.top,
                        right = winLeft + node.left + node.width,
                        bottom = winTop + node.top + node.height,
                        clickable = node.isClickable,
                        focusable = node.isFocusable,
                        visibleToUser = true
                    )
                )
            }
            for (i in 0 until node.childCount) {
                val child = node.getChildAt(i)
                // 尝试累加当前节点偏移量
                val newWinLeft = winLeft + node.left
                val newWinTop = winTop + node.top
                dumpNode(child, pkgFallback, out, depth + 1, newWinLeft, newWinTop)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "dumpNode 跳过: ${t.message}")
        }
    }

    fun getSnapshot(): List<UiObjectLite> = snapshot

    fun getVersion(): Int = version

    fun clear() {
        snapshot = emptyList()
    }
}