package com.seeme.app.media

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.seeme.app.SeeMeService
import com.seeme.app.util.LogBuffer

/**
 * 媒体播放状态监听（借鉴 SleepyXposed 的通知监听方式，跨 ROM 兼容）。
 * 从 MediaPlayback 通知中提取标题/艺术家，写入 SeeMeService 状态并随上报发送。
 * 需用户在系统设置中授予"通知使用权"。
 */
class SeeMeMediaListener : NotificationListenerService() {

    companion object {
        @Volatile
        var isEnabled: Boolean = false
            private set

        private const val TAG = "Media"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isEnabled = true
        LogBuffer.log(TAG, "listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || !isMediaNotification(sbn)) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        if (title.isNullOrBlank()) return
        val artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val full = if (artist.isNullOrBlank()) title else "$title - $artist"
        SeeMeService.setMediaTitle(full)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null || !isMediaNotification(sbn)) return
        // 媒体通知移除（停止播放/清除）→ 清空媒体标题
        SeeMeService.setMediaTitle(null)
    }

    private fun isMediaNotification(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification ?: return false
        return n.category == Notification.CATEGORY_TRANSPORT ||
            sbn.packageName in MEDIA_PACKAGES
    }

    override fun onDestroy() {
        isEnabled = false
        super.onDestroy()
    }

    private companion object {
        val MEDIA_PACKAGES = setOf(
            "com.spotify.music",
            "com.netease.cloudmusic",
            "com.tencent.qqmusic",
            "com.kugou.android",
            "com.miui.player",
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music",
            "com.bilibili.app.blue",
            "com.zhiliaoapp.musically",
            "tv.danmaku.bili",
            "com.kuaishou.nebula",
        )
    }
}
