package com.aifriend.assistant

import android.app.assist.AssistStructure
import android.util.Log

/**
 * AssistStructure 内存缓存
 * - 仿 vis 的 AssistStructureCache，存 UiObjectLite 列表
 * - Vis 进程单例；EC 通过 Binder 远程调用 searchByText/...
 * - 每次有新的 AssistStructure 进来时，先 clear 再填充
 * - 版本号单调递增，EC 用它判断是否需要重读
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
                dumpNode(window.rootNode, pkg, newSnapshot)
            }
            snapshot = newSnapshot
            version++
            Log.d(TAG, "快照已更新 version=$version nodes=${newSnapshot.size}")
        } catch (t: Throwable) {
            Log.e(TAG, "更新失败: ${t.message}", t)
        }
    }

    private fun dumpNode(
        node: android.app.assist.AssistStructure.ViewNode,
        pkgFallback: String,
        out: ArrayList<UiObjectLite>
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
                        left = node.left,
                        top = node.top,
                        right = node.right,
                        bottom = node.bottom,
                        clickable = node.isClickable,
                        focusable = node.isFocusable,
                        visibleToUser = node.isVisibleToUser
                    )
                )
            }
            for (i in 0 until node.childCount) {
                dumpNode(node.getChildAt(i), pkgFallback, out)
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