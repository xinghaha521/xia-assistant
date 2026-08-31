package com.aifriend.assistant

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.aifriend.assistant.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主页面：引导开启权限 + 调试信息展示
 *
 * 布局说明：
 * - 上半部分：权限引导（数字助理、无障碍、自启动、电池优化）
 * - 下半部分：调试信息（连接数、推送次数、最后事件、包大小、运行时长）
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_STORAGE = 1001
        private const val ASSISTANT_SERVICE_NAME = "com.aifriend.assistant/.VoiceCommandService"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: DebugViewModel
    private val uptimeHandler = Handler(Looper.getMainLooper())
    private val uptimeRunnable = object : Runnable {
        override fun run() {
            viewModel.tickUptime()
            uptimeHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[DebugViewModel::class.java]

        // 首次启动：释放 JS 模块到 files/js/（自愈式，缺啥补啥）
        JsModuleRelease.ensureReleased(this)

        setupUI()
        observeState()
        observeNodePusher()
    }

    private fun setupUI() {
        binding.btnSetDefaultAssistant.setOnClickListener {
            openAssistantSettings()
        }
        binding.btnOpenAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }
        binding.btnOpenBatteryOpt.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }
        binding.btnOpenAutoStart.setOnClickListener {
            openAutoStartSettings()
        }
        binding.btnStartService.setOnClickListener {
            AssistForegroundService.start(this)
        }
        binding.btnStopService.setOnClickListener {
            AssistForegroundService.stop(this)
        }
        binding.btnResetStats.setOnClickListener {
            viewModel.reset()
        }
    }

    private fun observeState() {
        viewModel.stats.observe(this) { stats ->
            binding.tvClientCount.text = "当前客户端连接：${stats.clientCount}"
            binding.tvPushCount.text = "总推送次数：${stats.pushCount}"
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            binding.tvLastEvent.text = "最后事件：${timeFmt.format(Date(stats.lastEventTime))}"
            binding.tvLastSize.text = "最后包大小：${Formatter.formatShortFileSize(this, stats.lastPacketSize)}"
            val durationSec = stats.serviceUptimeMs / 1000
            val h = durationSec / 3600
            val m = (durationSec % 3600) / 60
            val s = durationSec % 60
            binding.tvUptime.text = "服务运行：%02d:%02d:%02d".format(h, m, s)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
        viewModel.onServiceStart()
        uptimeHandler.postDelayed(uptimeRunnable, 1000L)
    }

    override fun onPause() {
        super.onPause()
        uptimeHandler.removeCallbacks(uptimeRunnable)
    }

    private fun observeNodePusher() {
        NodePusher.pushEventLiveData.observe(this) { event ->
            viewModel.onPush(event.packetSize, event.clientCount)
        }
        NodePusher.clientCountLiveData.observe(this) { count ->
            viewModel.onClientCountChanged(count)
        }
    }

    /**
     * 刷新四个权限开关的显示
     */
    private fun refreshPermissionStates() {
        // 1. 默认数字助理
        val isDefault = isDefaultVoiceAssistant()
        binding.tvAssistantStatus.text = if (isDefault) {
            "① 默认数字助理：【已设为小A服务】 ✓"
        } else {
            "① 默认数字助理：【未设】 ✗"
        }
        binding.tvAssistantStatus.setTextColor(
            if (isDefault) getColor(android.R.color.holo_green_dark)
            else getColor(android.R.color.holo_red_dark)
        )
        binding.btnSetDefaultAssistant.visibility = if (isDefault) View.GONE else View.VISIBLE

        // 2. 无障碍服务（始终 GONE，保留入口备用）
        binding.tvAccessibilityStatus.text = "② 无障碍服务：可选备用通道"
        binding.tvAccessibilityStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
        binding.btnOpenAccessibility.visibility = View.GONE

        // 3. 自启动（无法直接判断，提示用户）
        binding.tvAutoStartStatus.text = "③ 自启动权限：请到系统设置手动开启"
        binding.tvAutoStartStatus.setTextColor(getColor(android.R.color.holo_orange_dark))

        // 4. 电池优化
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignoringBattery = pm.isIgnoringBatteryOptimizations(packageName)
        binding.tvBatteryStatus.text = if (ignoringBattery) "④ 电池优化：【已忽略】 ✓" else "④ 电池优化：【未忽略】 ✗"
        binding.tvBatteryStatus.setTextColor(
            if (ignoringBattery) getColor(android.R.color.holo_green_dark)
            else getColor(android.R.color.holo_red_dark)
        )
        binding.btnOpenBatteryOpt.visibility = if (ignoringBattery) View.GONE else View.VISIBLE
    }

    /**
     * 检测本应用是否为系统默认数字助理
     * 注意：MIUI 12.5 偶发反篡改，最稳是返回
     - 检测后还要看 system_server 是否真的拉起
     */
    private fun isDefaultVoiceAssistant(): Boolean {
        return try {
            val current = Settings.Secure.getString(contentResolver, "voice_interaction_service")
            current != null && current.contains(packageName)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 跳转到无障碍设置
     */
    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("请手动进入 设置 → 无障碍 → 找到【${getString(R.string.accessibility_service_label)}】并开启")
                .setPositiveButton("好的", null)
                .show()
        }
    }

    /**
     * 跳转到默认数字助理设置页
     *
     * MIUI 12.5 实测：com.android.settings.Settings$ManageAssistActivity 可直接跳转
     * 等价的 Intent action 是 com.android.settings.action.VOICE_INPUT_SETTINGS
     * 跳过去后用户在系统设置里点选【小a】即生效
     */
    private fun openAssistantSettings() {
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.android.settings",
                    "com.android.settings.Settings\$ManageAssistActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                // 兜底：用 action 跳转
                startActivity(Intent("com.android.settings.action.VOICE_INPUT_SETTINGS").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (e2: Exception) {
                AlertDialog.Builder(this)
                    .setTitle("跳转失败")
                    .setMessage("请手动进入 设置 → 助手和语音输入 → 选择【小A服务】作为默认数字助理")
                    .setPositiveButton("好的", null)
                    .show()
            }
        }
    }

    /**
     * 申请电池优化白名单
     */
    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    /**
     * 各厂商自启动设置入口（尽力而为）
     */
    private fun openAutoStartSettings() {
        val intents = listOf(
            Intent().setComponent(android.content.ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )),
            Intent().setComponent(android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )),
            Intent().setComponent(android.content.ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )),
            Intent().setComponent(android.content.ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            ))
        )
        for (intent in intents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                return
            } catch (_: Exception) {
                // 继续尝试下一个
            }
        }
        AlertDialog.Builder(this)
            .setTitle("请手动开启自启动")
            .setMessage("请手动进入 设置 → 应用管理 → 【小A服务】 → 自启动 → 打开")
            .setPositiveButton("好的", null)
            .show()
    }
}