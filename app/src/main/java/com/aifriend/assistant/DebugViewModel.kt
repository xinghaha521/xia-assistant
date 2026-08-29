package com.aifriend.assistant

import android.os.SystemClock
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * 调试状态数据
 *
 * @param clientCount 当前已连接的客户端数
 * @param pushCount 累计推送次数
 * @param lastEventTime 最后一次事件时间戳（System.currentTimeMillis）
 * @param lastPacketSize 最后一次推送包大小（字节）
 * @param serviceUptimeMs 服务运行时长（毫秒）
 */
data class DebugStats(
    val clientCount: Int = 0,
    val pushCount: Long = 0,
    val lastEventTime: Long = 0L,
    val lastPacketSize: Long = 0L,
    val serviceUptimeMs: Long = 0L
)

/**
 * 调试页 ViewModel
 *
 * 设计要点：
 * - 单进程内通过静态字段直写，跨进程通过 NodePusher 回调
 * - 线程安全：所有更新都在主线程（NodePusher 通过 postValue 回主线程）
 */
class DebugViewModel : ViewModel() {

    private val _stats = MutableLiveData<DebugStats>()
    val stats: LiveData<DebugStats> = _stats

    private var current = DebugStats()
    private var serviceStartTime = 0L

    init {
        _stats.value = current
    }

    /**
     * 推送统计（NodePusher 在推送时回调）
     */
    fun onPush(packetSize: Int, clientCount: Int) {
        current = current.copy(
            pushCount = current.pushCount + 1,
            lastEventTime = System.currentTimeMillis(),
            lastPacketSize = packetSize.toLong(),
            clientCount = clientCount
        )
        _stats.value = current
    }

    /**
     * 客户端连接数变化
     */
    fun onClientCountChanged(count: Int) {
        current = current.copy(clientCount = count)
        _stats.value = current
    }

    /**
     * 服务启动时调用，记录启动时间
     */
    fun onServiceStart() {
        if (serviceStartTime == 0L) {
            serviceStartTime = SystemClock.uptimeMillis()
        }
        current = current.copy(
            serviceUptimeMs = SystemClock.uptimeMillis() - serviceStartTime
        )
        _stats.value = current
    }

    /**
     * 服务停止时调用
     */
    fun onServiceStop() {
        serviceStartTime = 0L
        current = current.copy(serviceUptimeMs = 0L)
        _stats.value = current
    }

    /**
     * 每秒刷新一次运行时长（由 Activity 定时调用）
     */
    fun tickUptime() {
        if (serviceStartTime == 0L) return
        current = current.copy(
            serviceUptimeMs = SystemClock.uptimeMillis() - serviceStartTime
        )
        _stats.value = current
    }

    /**
     * 清空调试数据
     */
    fun reset() {
        current = DebugStats()
        _stats.value = current
    }
}