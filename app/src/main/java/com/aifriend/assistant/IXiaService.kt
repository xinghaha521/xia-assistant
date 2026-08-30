package com.aifriend.assistant

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.Parcelable
import android.os.RemoteException
import android.util.Log

/**
 * 自定义 Binder 接口（不依赖 aidl 编译，避免 CI 缓存问题）
 * 与 IXiaService.aidl 等价
 */
interface IXiaService : IInterface {

    fun triggerAssistSession()
    fun getCurrentSnapshot(): List<UiObjectLite>
    fun registerCallback(callback: IXiaCallback?)
    fun unregisterCallback(callback: IXiaCallback?)
    fun getSnapshotVersion(): Int

    companion object {
        const val DESCRIPTOR = "com.aifriend.assistant.IXiaService"
        const val TRANSACTION_triggerAssistSession = IBinder.FIRST_CALL_TRANSACTION + 0
        const val TRANSACTION_getCurrentSnapshot = IBinder.FIRST_CALL_TRANSACTION + 1
        const val TRANSACTION_registerCallback = IBinder.FIRST_CALL_TRANSACTION + 2
        const val TRANSACTION_unregisterCallback = IBinder.FIRST_CALL_TRANSACTION + 3
        const val TRANSACTION_getSnapshotVersion = IBinder.FIRST_CALL_TRANSACTION + 4

        fun asInterface(binder: IBinder?): IXiaService? {
            if (binder == null) return null
            val iin = binder.queryLocalInterface(DESCRIPTOR)
            return if (iin != null && iin is IXiaService) iin else Proxy(binder)
        }
    }

    /**
     * 服务端 Stub（在本进程里实现）
     */
    abstract class Stub : Binder(), IXiaService {
        init {
            attachInterface(this, DESCRIPTOR)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return try {
                when (code) {
                    INTERFACE_TRANSACTION -> {
                        reply?.writeString(DESCRIPTOR)
                        true
                    }
                    TRANSACTION_triggerAssistSession -> {
                        data.enforceInterface(DESCRIPTOR)
                        triggerAssistSession()
                        true
                    }
                    TRANSACTION_getCurrentSnapshot -> {
                        data.enforceInterface(DESCRIPTOR)
                        val result = getCurrentSnapshot()
                        reply?.writeString(DESCRIPTOR)
                        reply?.writeInt(result.size)
                        for (n in result) {
                            n.writeToParcel(reply!!, 0)
                        }
                        true
                    }
                    TRANSACTION_registerCallback -> {
                        data.enforceInterface(DESCRIPTOR)
                        val cb = IXiaCallback.asInterface(data.readStrongBinder())
                        registerCallback(cb)
                        true
                    }
                    TRANSACTION_unregisterCallback -> {
                        data.enforceInterface(DESCRIPTOR)
                        val cb = IXiaCallback.asInterface(data.readStrongBinder())
                        unregisterCallback(cb)
                        true
                    }
                    TRANSACTION_getSnapshotVersion -> {
                        data.enforceInterface(DESCRIPTOR)
                        val v = getSnapshotVersion()
                        reply?.writeString(DESCRIPTOR)
                        reply?.writeInt(v)
                        true
                    }
                    else -> super.onTransact(code, data, reply, flags)
                }
            } catch (t: Throwable) {
                Log.w("IXiaService", "onTransact failed code=$code: ${t.message}")
                false
            }
        }

        /**
         * 客户端 Proxy（跨进程调用）
         */
        private class Proxy(private val remote: IBinder) : IXiaService {
            override fun triggerAssistSession() {
                val data = Parcel()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    remote.transact(TRANSACTION_triggerAssistSession, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }

            override fun getCurrentSnapshot(): List<UiObjectLite> {
                val data = Parcel()
                val reply = Parcel()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    remote.transact(TRANSACTION_getCurrentSnapshot, data, reply, 0)
                    val size = reply.readInt()
                    val list = ArrayList<UiObjectLite>(size)
                    repeat(size) {
                        list.add(UiObjectLite.CREATOR.createFromParcel(reply))
                    }
                    list
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }

            override fun registerCallback(callback: IXiaCallback?) {
                val data = Parcel()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeStrongBinder(callback?.asBinder())
                    remote.transact(TRANSACTION_registerCallback, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }

            override fun unregisterCallback(callback: IXiaCallback?) {
                val data = Parcel()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeStrongBinder(callback?.asBinder())
                    remote.transact(TRANSACTION_unregisterCallback, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }

            override fun getSnapshotVersion(): Int {
                val data = Parcel()
                val reply = Parcel()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    remote.transact(TRANSACTION_getSnapshotVersion, data, reply, 0)
                    reply.readInt()
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }

            override fun asBinder(): IBinder = remote
        }

        override fun asBinder(): IBinder = this
    }
}