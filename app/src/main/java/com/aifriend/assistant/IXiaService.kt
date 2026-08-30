package com.aifriend.assistant

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.util.Log

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
            return if (iin != null && iin is IXiaService) iin else IXiaServiceProxy(binder)
        }
    }

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

        override fun asBinder(): IBinder = this
    }
}

private class IXiaServiceProxy(private val remote: IBinder) : IXiaService {

    override fun triggerAssistSession() {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(IXiaService.DESCRIPTOR)
            remote.transact(IXiaService.TRANSACTION_triggerAssistSession, data, null, IBinder.FLAG_ONEWAY)
        } finally {
            data.recycle()
        }
    }

    override fun getCurrentSnapshot(): List<UiObjectLite> {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(IXiaService.DESCRIPTOR)
            remote.transact(IXiaService.TRANSACTION_getCurrentSnapshot, data, reply, 0)
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
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(IXiaService.DESCRIPTOR)
            data.writeStrongBinder(callback?.asBinder())
            remote.transact(IXiaService.TRANSACTION_registerCallback, data, null, IBinder.FLAG_ONEWAY)
        } finally {
            data.recycle()
        }
    }

    override fun unregisterCallback(callback: IXiaCallback?) {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(IXiaService.DESCRIPTOR)
            data.writeStrongBinder(callback?.asBinder())
            remote.transact(IXiaService.TRANSACTION_unregisterCallback, data, null, IBinder.FLAG_ONEWAY)
        } finally {
            data.recycle()
        }
    }

    override fun getSnapshotVersion(): Int {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(IXiaService.DESCRIPTOR)
            remote.transact(IXiaService.TRANSACTION_getSnapshotVersion, data, reply, 0)
            reply.readInt()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    override fun asBinder(): IBinder = remote
}