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
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.aifriend.assistant.databinding.ActivityMainBinding

/**
 * 主页面：权限引导（精简版）+ 服务控制 + 运行模式选择
 *
 * v0.8.0 简化：
 * - 权限引导只保留可检测的 2 项（数字助理 + 电池优化）
 * - 新增运行模式 RadioGroup（数字模式/无障碍模式）
 * - 无障碍模式 + 未开启无障碍时显示警告 banner
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

        setupUI()
        observeState()
    }

    private fun setupUI() {
        binding.btnSetDefaultAssistant.setOnClickListener { openAssistantSettings() }
        binding.btnOpenBatteryOpt.setOnClickListener { requestIgnoreBatteryOptimization() }
        binding.btnStartService.setOnClickListener { AssistForegroundService.start(this) }
        binding.btnStopService.setOnClickListener { AssistForegroundService.stop(this) }

        binding.rgMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rbAccessibility) {
                XiaSettings.MODE_ACCESSIBILITY
            } else {
                XiaSettings.MODE_DIGITAL
            }
            XiaSettings.setMode(this, mode)
            val msg = if (mode == XiaSettings.MODE_ACCESSIBILITY) {
                "已切换：无障碍模式"
            } else {
                "已切换：数字模式"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            refreshAccessibilityHint()
        }
    }

    private fun observeState() {
        viewModel.stats.observe(this) { stats ->
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
        refreshAccessibilityHint()
        viewModel.onServiceStart()
        uptimeHandler.postDelayed(uptimeRunnable, 1000L)
    }

    override fun onPause() {
        super.onPause()
        uptimeHandler.removeCallbacks(uptimeRunnable)
    }

    /**
     * 刷新权限引导状态（精简版：仅数字助理 + 电池优化）
     */
    private fun refreshPermissionStates() {
        // ① 默认数字助理
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

        // ② 电池优化
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignoringBattery = pm.isIgnoringBatteryOptimizations(packageName)
        binding.tvBatteryStatus.text = if (ignoringBattery) "② 电池优化：【已忽略】 ✓" else "② 电池优化：【未忽略】 ✗"
        binding.tvBatteryStatus.setTextColor(
            if (ignoringBattery) getColor(android.R.color.holo_green_dark)
            else getColor(android.R.color.holo_red_dark)
        )
        binding.btnOpenBatteryOpt.visibility = if (ignoringBattery) View.GONE else View.VISIBLE
    }

    /**
     * 刷新无障碍模式警告（仅在选择无障碍模式且无障碍未开启时显示）
     */
    private fun refreshAccessibilityHint() {
        val inAccMode = XiaSettings.isAccessibilityMode(this)
        val accEnabled = isAccessibilityEnabled()
        binding.tvAccessibilityHint.visibility =
            if (inAccMode && !accEnabled) View.VISIBLE else View.GONE
        // 同步 RadioGroup 选中状态（处理外部修改设置）
        val targetId = if (inAccMode) R.id.rbAccessibility else R.id.rbDigital
        if (binding.rgMode.checkedRadioButtonId != targetId) {
            binding.rgMode.check(targetId)
        }
    }

    /**
     * 检测本应用的无障碍服务是否已开启
     */
    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val services = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return services.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    /**
     * 检测本应用是否为系统默认数字助理
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
     * 跳转到默认数字助理设置页
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
}
