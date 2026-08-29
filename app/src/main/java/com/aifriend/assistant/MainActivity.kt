package com.aifriend.assistant

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
 * 主页面：引导开启无障碍 + 调试信息展示
 *
 * 布局说明：
 * - 上半部分：权限引导（无障碍、自启动、电池优化）
 * - 下半部分：调试信息（连接数、推送次数、最后事件、包大小、运行时长）
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_STORAGE = 1001
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: DebugViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[DebugViewModel::class.java]

        setupUI()
        observeState()
        requestStoragePermission()
    }

    /**
     * 请求存储权限（写入 /sdcard/xiaoa/ 与 EC 主程序共享节点 XML）
     */
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQ_STORAGE
                )
            }
        }
    }

    private fun setupUI() {
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
    }

    /**
     * 刷新三个权限开关的显示
     */
    private fun refreshPermissionStates() {
        // 1. 无障碍服务
        val accOn = isAccessibilityEnabled()
        binding.tvAccessibilityStatus.text = if (accOn) "① 无障碍服务：【已开启】 ✓" else "① 无障碍服务：【未开启】 ✗"
        binding.tvAccessibilityStatus.setTextColor(
            if (accOn) getColor(android.R.color.holo_green_dark) else getColor(android.R.color.holo_red_dark)
        )
        binding.btnOpenAccessibility.visibility = if (accOn) View.GONE else View.VISIBLE

        // 2. 自启动（无法直接判断，提示用户）
        binding.tvAutoStartStatus.text = "② 自启动权限：请到系统设置手动开启"
        binding.tvAutoStartStatus.setTextColor(getColor(android.R.color.holo_orange_dark))

        // 3. 电池优化
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignoringBattery = pm.isIgnoringBatteryOptimizations(packageName)
        binding.tvBatteryStatus.text = if (ignoringBattery) "③ 电池优化：【已忽略】 ✓" else "③ 电池优化：【未忽略】 ✗"
        binding.tvBatteryStatus.setTextColor(
            if (ignoringBattery) getColor(android.R.color.holo_green_dark) else getColor(android.R.color.holo_red_dark)
        )
        binding.btnOpenBatteryOpt.visibility = if (ignoringBattery) View.GONE else View.VISIBLE
    }

    /**
     * 检测无障碍服务是否已开启
     */
    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabled.any { it.resolveInfo.serviceInfo.packageName == packageName }
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
            .setMessage("设置 → 应用管理 → 【小a】 → 自启动 → 打开")
            .setPositiveButton("好的", null)
            .show()
    }
}