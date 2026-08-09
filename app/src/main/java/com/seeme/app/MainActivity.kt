package com.seeme.app

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.seeme.app.config.Config
import com.seeme.app.databinding.ActivityMainBinding
import com.seeme.app.root.RootManager
import com.seeme.app.util.LogBuffer

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ---- 服务器配置 ----
        binding.urlInput.setText(Config.serverUrl(this))
        binding.tokenInput.setText(Config.authToken(this))
        binding.deviceInfo.text = getString(
            com.seeme.app.R.string.device_info,
            Config.deviceId(this),
            Config.deviceName(),
        )

        binding.saveBtn.setOnClickListener {
            Config.saveServerUrl(this, binding.urlInput.text.toString())
            Config.saveAuthToken(this, binding.tokenInput.text.toString())
            SeeMeService.stop(this)
            SeeMeService.start(this)
            LogBuffer.log("UI", "保存服务器配置")
        }

        binding.startBtn.setOnClickListener {
            SeeMeService.start(this)
            LogBuffer.log("UI", "启动服务")
        }
        binding.stopBtn.setOnClickListener {
            SeeMeService.stop(this)
            LogBuffer.log("UI", "停止服务")
        }

        // ---- 权限入口 ----
        binding.a11yBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.usageBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.batteryBtn.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = getSystemService(PowerManager::class.java)
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    )
                }
            }
        }

        // ---- 自定义在线状态（借鉴 sleepy） ----
        binding.statusInput.setText(Config.statusText(this))
        binding.saveStatusBtn.setOnClickListener {
            Config.saveStatusText(this, binding.statusInput.text.toString())
            SeeMeService.triggerReport(this)
            LogBuffer.log("UI", "保存自定义状态: ${binding.statusInput.text}")
        }

        // ---- Root 管理 ----
        binding.rootStatus.text = "Root: ${if (RootManager.isRootAvailable()) "✅ 可用" else "❌ 不可用"}"

        binding.checkRootBtn.setOnClickListener {
            val ok = RootManager.isRootAvailable()
            binding.rootStatus.text = "Root: ${if (ok) "✅ 可用" else "❌ 不可用"}"
            LogBuffer.log("UI", "Root 检测: $ok")
        }

        binding.requestRootBtn.setOnClickListener {
            binding.rootStatus.text = "正在请求授权…"
            Thread {
                val ok = RootManager.requestRoot()
                runOnUiThread {
                    binding.rootStatus.text = "Root 授权: ${if (ok) "✅ 已授予" else "❌ 未授予/无 su"}"
                }
            }.start()
        }

        binding.installSystemBtn.setOnClickListener {
            binding.rootStatus.text = "正在安装为系统应用…"
            Thread {
                val result = RootManager.installAsSystemApp(this)
                runOnUiThread {
                    binding.rootStatus.text = "安装结果: ${result.take(150)}"
                }
            }.start()
        }

        // ---- 媒体状态上报（借鉴 SleepyXposed） ----
        binding.mediaSwitch.isChecked = Config.mediaEnabled(this)
        binding.mediaSwitch.setOnCheckedChangeListener { _, checked ->
            Config.saveMediaEnabled(this, checked)
            LogBuffer.log("UI", "媒体上报: ${if (checked) "开" else "关"}")
        }
        binding.grantMediaBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // ---- 后台隐藏 ----
        binding.hideIconSwitch.isChecked = !isLauncherVisible()
        binding.hideIconSwitch.setOnCheckedChangeListener { _, checked ->
            setLauncherVisible(!checked)
            LogBuffer.log("UI", "隐藏图标: $checked")
        }

        // ---- 日志 ----
        binding.refreshLogBtn.setOnClickListener { refreshLog() }
        binding.clearLogBtn.setOnClickListener {
            LogBuffer.clear()
            binding.logView.text = "（暂无日志）"
        }

        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refreshLog()
    }

    private fun refreshLog() {
        val logs = LogBuffer.dump()
        binding.logView.text = if (logs.isBlank()) "（暂无日志）" else logs
    }

    private fun launcherComponent(): ComponentName =
        ComponentName(this, "${packageName}.MainActivity")

    private fun isLauncherVisible(): Boolean =
        packageManager.getComponentEnabledSetting(launcherComponent()) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    private fun setLauncherVisible(visible: Boolean) {
        packageManager.setComponentEnabledSetting(
            launcherComponent(),
            if (visible) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1
            )
        }
    }
}
