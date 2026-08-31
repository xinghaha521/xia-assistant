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
                    // v0.8.1: 传递窗口偏移量，用于把节点本地坐标转换为屏幕绝对坐标
                    val winLeft = try { window.left } catch (t: Throwable) { 0 }
                    val winTop = try { window.top } catch (t: Throwable) { 0 }
                    dumpNode(root, pkg, newSnapshot, winLeft, winTop)
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
        winLeft: Int = 0,
        winTop: Int = 0
    ) {
        try {
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            val rid = node.idEntry ?: ""
            val cls = node.className ?: ""
            val pkg = node.idPackage ?: pkgFallback
            if (text.isNotEmpty() || desc.isNotEmpty() || rid.isNotEmpty() || cls.isNotEmpty()) {
                // v0.8.1: 用 WindowNode.getLeft()/getTop() 拿屏幕偏移，加上节点本地坐标
                // = 屏幕绝对坐标。AssistStructure.ViewNode 没有 getBoundsInScreen()，
                // 只能用这种"窗口偏移 + 节点本地"的组合方案（无 transform/scroll 缩放近似）
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
                dumpNode(node.getChildAt(i), pkgFallback, out, winLeft, winTop)
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