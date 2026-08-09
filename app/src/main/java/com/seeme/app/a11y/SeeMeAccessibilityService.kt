package com.seeme.app.a11y

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.seeme.app.SeeMeService

/**
 * 无障碍采集服务：
 * - TYPE_WINDOW_STATE_CHANGED / TYPE_WINDOWS_CHANGED 捕获前台窗口切换
 * - 识别输入法（IME）窗口出现/消失 → 输入状态 typing / idle
 */
class SeeMeAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var isActive: Boolean = false
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isActive = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return

        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString()

        if (isImeWindow(pkg, cls)) {
            // 键盘弹出
            SeeMeService.setInputState("typing")
        } else {
            // 键盘收起（或切到普通窗口）
            SeeMeService.setInputState("idle")
            if (pkg != SeeMeService.foregroundAppPkg) {
                val label = resolveAppLabel(pkg)
                val activity = if (cls == null) pkg else {
                    if (cls.startsWith(pkg)) cls else "$pkg/$cls"
                }
                SeeMeService.setForeground(pkg, label, activity)
            }
        }
    }

    private fun isImeWindow(pkg: String, cls: String?): Boolean {
        val lower = (pkg + " " + (cls ?: "")).lowercase()
        return lower.contains("inputmethod") ||
            lower.contains(".ime") ||
            lower.contains("keyboard")
    }

    private fun resolveAppLabel(pkg: String): String {
        return runCatching {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        }.getOrDefault(pkg)
    }

    override fun onInterrupt() {
        // no-op
    }

    override fun onDestroy() {
        isActive = false
        super.onDestroy()
    }
}
