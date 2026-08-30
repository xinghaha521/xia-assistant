package com.aifriend.assistant

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

/**
 * 自定义回调接口（不依赖 aidl 编译）
 */
interface IXiaCallback : IInterface {
    fun onSnapshotUpdated(version: Int)

    companion object {
        const val DESCRIPTOR = "com.aifriend.assistant.IXiaCallback"
        const val TRANSACTION_onSnapshotUpdated = IBinder.FIRST_CALL_TRANSACTION + 0

        fun asInterface(binder: IBinder?): IXiaCallback? {
            if (binder == null) return null
            val iin = binder.queryLocalInterface(DESCRIPTOR)
            return if (iin != null && iin is IXiaCallback) iin else Proxy(binder)
        }
    }

    abstract class Stub : Binder(), IXiaCallback {
        init {
            attachInterface(this, DESCRIPTOR)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESCRIPTOR)
                    true
                }
                TRANSACTION_onSnapshotUpdated -> {
                    data.enforceInterface(DESCRIPTOR)
                    val v = data.readInt()
                    onSnapshotUpdated(v)
                    true
                }
                else -> super.onTransact(code, data, reply, flags)
            }
        }

        override fun asBinder(): IBinder = this

        private class Proxy(private val remote: IBinder) : IXiaCallback {
            override fun onSnapshotUpdated(version: Int) {
                val data = Parcel()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeInt(version)
                    remote.transact(TRANSACTION_onSnapshotUpdated, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }

            override fun asBinder(): IBinder = remote
        }
    }
}