package com.seeme.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.seeme.app.a11y.SeeMeAccessibilityService
import com.seeme.app.config.Config
import com.seeme.app.reporter.Reporter
import org.json.JSONObject

/**
 * 前台采集服务：
 * - 电池电量（动态广播）
 * - 前台应用/输入状态由 AccessibilityService 写入（SeeMeService 静态状态）
 * - UsageStats 兜底轮询（无障碍未开启时）
 * - 事件驱动上报 + 60s 心跳
 */
class SeeMeService : Service() {

    companion object {
        private const val TAG = "SeeMeService"
        private const val NOTIF_ID = 1
        private const val HEARTBEAT_MS = 60_000L
        private const val USAGE_FALLBACK_MS = 30_000L

        const val ACTION_START = "com.seeme.app.action.START"
        const val ACTION_STOP = "com.seeme.app.action.STOP"

        // ---- 全局采集状态（AccessibilityService / UI 读写） ----
        @Volatile var batteryLevel: Int = -1
        @Volatile var charging: Boolean = false
        @Volatile var foregroundAppPkg: String? = null
        @Volatile var foregroundApp: String? = null
        @Volatile var foregroundActivity: String? = null
        @Volatile var inputStateValue: String = "unknown"

        /** 上次上报内容的快照，用于事件去重 */
        private var lastReported = JSONObject()

        @Synchronized
        fun setForeground(pkg: String, label: String, activity: String?) {
            foregroundAppPkg = pkg
            foregroundApp = label
            foregroundActivity = activity
            markChanged()
        }

        @Synchronized
        fun setInputState(state: String) {
            if (inputStateValue != state) {
                inputStateValue = state
                markChanged()
            }
        }

        private var changeListenerRef: (() -> Unit)? = null

        @Synchronized
        fun setChangeListener(listener: (() -> Unit)?) {
            changeListenerRef = listener
        }

        private fun markChanged() {
            changeListenerRef?.invoke()
        }

        fun start(context: Context) {
            if (!Config.isConfigured(context)) return
            val intent = Intent(context, SeeMeService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, SeeMeService::class.java).setAction(ACTION_STOP))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var reporter: Reporter

    private val heartbeat = object : Runnable {
        override fun run() {
            readBattery()
            usageStatsFallback()
            report()
            handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val before = batteryLevel
            val beforeCharging = charging
            readBattery(intent)
            if (before != batteryLevel || beforeCharging != charging) {
                report()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        reporter = Reporter(Config.serverUrl(this), Config.authToken(this))
        createNotificationChannel()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // 无障碍服务写入状态后触发上报
        setChangeListener {
            handler.post { report() }
        }

        // 启动前台通知
        readBattery()
        startForeground(NOTIF_ID, buildNotification())

        handler.post(heartbeat)
        Log.i(TAG, "service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // ACTION_START / 重启恢复：重读配置
                reporter = Reporter(Config.serverUrl(this), Config.authToken(this))
                readBattery()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        setChangeListener(null)
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(batteryReceiver) }
        Log.i(TAG, "service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- 电池 ----------

    private fun readBattery(intent: Intent? = null) {
        val i = intent ?: registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        batteryLevel = if (level >= 0 && scale > 0) level * 100 / scale else -1
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    // ---------- UsageStats 兜底 ----------

    private fun usageStatsFallback() {
        if (SeeMeAccessibilityService.isActive) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        if (checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - 60_000, end)
        val event = UsageEvents.Event()
        var pkg: String? = null
        var cls: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                pkg = event.packageName
                cls = event.className
            }
        }
        if (pkg != null && pkg != foregroundAppPkg) {
            val label = runCatching {
                val ai = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(ai).toString()
            }.getOrDefault(pkg)
            setForeground(pkg, label, if (cls != null) "$pkg/$cls" else null)
        }
    }

    // ---------- 上报 ----------

    @Synchronized
    private fun report() {
        if (!Config.isConfigured(this)) return
        val level = if (batteryLevel >= 0) batteryLevel else null
        val state = JSONObject().apply {
            put("batteryLevel", level ?: -1)
            put("charging", charging)
            put("foregroundApp", foregroundApp ?: "")
            put("foregroundActivity", foregroundActivity ?: "")
            put("inputState", inputStateValue)
        }
        if (state.toString() == lastReported.toString()) {
            // 无变化：心跳仍维持在线，但跳过重复上报
            if (System.currentTimeMillis() - lastReportAt < 30_000) return
        }

        reporter.report(
            deviceId = Config.deviceId(this),
            deviceName = Config.deviceName(),
            batteryLevel = level,
            charging = if (level != null) charging else null,
            foregroundApp = foregroundApp,
            foregroundActivity = foregroundActivity,
            inputState = inputStateValue,
        ) { success ->
            if (success) {
                synchronized(this) {
                    lastReported = state
                    lastReportAt = System.currentTimeMillis()
                }
                handler.post { updateNotification() }
            }
        }
    }

    private var lastReportAt = 0L

    // ---------- 通知 ----------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                getString(com.seeme.app.R.string.notif_channel),
                getString(com.seeme.app.R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (batteryLevel >= 0) {
            getString(
                com.seeme.app.R.string.notif_text,
                batteryLevel,
                if (charging) "充电中" else "未充电",
            )
        } else {
            "正在采集…"
        }
        return NotificationCompat.Builder(this, getString(com.seeme.app.R.string.notif_channel))
            .setSmallIcon(com.seeme.app.R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(com.seeme.app.R.string.notif_title))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification())
    }
}
