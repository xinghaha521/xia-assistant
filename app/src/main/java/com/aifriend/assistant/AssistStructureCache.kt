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
                    // v0.8.1: 用 WindowNode.getLeft()/getTop() 拿窗口偏移，作为递归累加的起点
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

    /**
     * v0.8.1 关键修复：递归累加父节点偏移
     *
     * AssistStructure.ViewNode.left/top 是相对父节点的本地坐标（不是屏幕绝对）。
     * 这与 AccessibilityNodeInfo.getBoundsInScreen() 的语义完全不同。
     * 早期版本直接用 node.left/top 当屏幕坐标，导致 RadioButton 等
     * 相对父布局的节点 bounds 全错。
     *
     * 修复方案：把当前节点的 left/top 加到 winLeft/winTop 上传给子节点，
     * 层层累加得到屏幕绝对坐标。WindowNode.getLeft()/getTop() 是根偏移起点。
     *
     * 局限：没处理 transform 矩阵和 scroll 偏移。绝大多数应用场景足够准确。
     */
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
                // 累加当前节点偏移，作为下一层递归的 winLeft/winTop
                val newWinLeft = winLeft + node.left
                val newWinTop = winTop + node.top
                dumpNode(child, pkgFallback, out, newWinLeft, newWinTop)
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