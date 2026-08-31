package com.aifriend.assistant

import android.os.SystemClock
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * 调试状态数据（v1.1.0 简化）
 * 只保留服务运行时长，移除客户端连接/推送次数/最后事件/包大小等冗余字段
 */
data class DebugStats(
    val serviceUptimeMs: Long = 0L
)

/**
 * 调试页 ViewModel
 *
 * 设计要点：
 * - 只暴露服务运行时长
 * - tickUptime() 每秒由 Activity 定时调用刷新
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
}