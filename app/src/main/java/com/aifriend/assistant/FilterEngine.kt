package com.aifriend.assistant

import android.util.Log

/**
 * 仿 vis 过滤器引擎的精简版
 * 首批支持 6 个核心过滤器：
 *   Id / Text / Desc / Bounds / ClassName / PackageName
 *
 * 返回所有匹配的 UiObjectLite，按原顺序
 */
object FilterEngine {

    private const val TAG = "FilterEngine"

    fun search(
        snapshot: List<UiObjectLite>,
        id: String? = null,
        text: String? = null,
        desc: String? = null,
        className: String? = null,
        packageName: String? = null,
        boundsLeft: Int? = null,
        boundsTop: Int? = null,
        boundsRight: Int? = null,
        boundsBottom: Int? = null
    ): List<UiObjectLite> {
        var result: List<UiObjectLite> = snapshot
        if (!id.isNullOrEmpty()) {
            result = result.filter { it.resourceId.equals(id, ignoreCase = true) }
        }
        if (!text.isNullOrEmpty()) {
            result = result.filter { it.text.equals(text, ignoreCase = true) }
        }
        if (!desc.isNullOrEmpty()) {
            result = result.filter { it.desc.equals(desc, ignoreCase = true) }
        }
        if (!className.isNullOrEmpty()) {
            result = result.filter { it.className == className }
        }
        if (!packageName.isNullOrEmpty()) {
            result = result.filter { it.packageName == packageName }
        }
        if (boundsLeft != null) {
            result = result.filter { it.left == boundsLeft }
        }
        if (boundsTop != null) {
            result = result.filter { it.top == boundsTop }
        }
        if (boundsRight != null) {
            result = result.filter { it.right == boundsRight }
        }
        if (boundsBottom != null) {
            result = result.filter { it.bottom == boundsBottom }
        }
        Log.d(TAG, "search 过滤后节点数=${result.size}")
        return result
    }
}