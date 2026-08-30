package com.aifriend.assistant

import android.os.Parcel
import android.os.Parcelable

/**
 * 轻量化的 UI 节点，跨进程传输友好
 * - 不携带 viewId（仅本进程有效）
 * - 不携带 AssistStructure 引用
 * - 只保留 EC 选择器需要的字段 + bounds
 */
data class UiObjectLite(
    val text: String = "",
    val desc: String = "",
    val resourceId: String = "",
    val className: String = "",
    val packageName: String = "",
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val clickable: Boolean = false,
    val focusable: Boolean = false,
    val visibleToUser: Boolean = true
) : Parcelable {

    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(text)
        dest.writeString(desc)
        dest.writeString(resourceId)
        dest.writeString(className)
        dest.writeString(packageName)
        dest.writeInt(left)
        dest.writeInt(top)
        dest.writeInt(right)
        dest.writeInt(bottom)
        dest.writeBoolean(clickable)
        dest.writeBoolean(focusable)
        dest.writeBoolean(visibleToUser)
    }

    companion object CREATOR : Parcelable.Creator<UiObjectLite> {
        override fun createFromParcel(source: Parcel): UiObjectLite {
            return UiObjectLite(
                text = source.readString() ?: "",
                desc = source.readString() ?: "",
                resourceId = source.readString() ?: "",
                className = source.readString() ?: "",
                packageName = source.readString() ?: "",
                left = source.readInt(),
                top = source.readInt(),
                right = source.readInt(),
                bottom = source.readInt(),
                clickable = source.readBoolean(),
                focusable = source.readBoolean(),
                visibleToUser = source.readBoolean()
            )
        }

        override fun newArray(size: Int): Array<UiObjectLite?> = arrayOfNulls(size)
    }
}