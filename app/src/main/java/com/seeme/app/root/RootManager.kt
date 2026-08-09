package com.seeme.app.root

import android.content.Context
import com.seeme.app.util.LogBuffer
import java.io.File

/** Root 检测、授权与系统应用保活（priv-app 安装） */
object RootManager {
    private const val TAG = "Root"

    /** 检测设备是否具备 root（su 是否存在） */
    fun isRootAvailable(): Boolean {
        return try {
            val suPaths = arrayOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/su/bin/su",
                "/data/adb/magisk",
                "/sbin/magisk",
            )
            if (suPaths.any { File(it).exists() }) {
                true
            } else {
                execSu("id") != null
            }
        } catch (e: Exception) {
            false
        }
    }

    /** 触发 su 授权弹窗并验证是否获得 root */
    fun requestRoot(): Boolean {
        val output = execSu("id")
        val granted = output != null && output.contains("uid=0")
        LogBuffer.log(TAG, "requestRoot granted=$granted ${output ?: "no su"}")
        return granted
    }

    /**
     * 将自身 APK 复制到 /system/priv-app 成为系统应用（需要 root）。
     * 返回执行输出；null 表示无 root。
     */
    fun installAsSystemApp(context: Context): String {
        val apkPath = context.applicationInfo.sourceDir
        val pkg = context.packageName
        val script = """
            mount -o rw,remount /system 2>/dev/null || mount -o rw,remount / 2>/dev/null
            mkdir -p /system/priv-app/SeeMe
            cp '$apkPath' /system/priv-app/SeeMe/SeeMe.apk
            chmod 644 /system/priv-app/SeeMe/SeeMe.apk
            chown root:root /system/priv-app/SeeMe/SeeMe.apk
            pm uninstall $pkg 2>/dev/null
            stop
            start
        """.trimIndent()
        val output = execSu(script)
        LogBuffer.log(TAG, "installAsSystemApp: ${output?.take(200) ?: "no root"}")
        return output ?: "未获得 root 权限"
    }

    /** 执行 su -c 命令，返回 stdout；失败返回 null */
    private fun execSu(command: String): String? {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val out = p.inputStream.bufferedReader().readText()
            val err = p.errorStream.bufferedReader().readText()
            p.waitFor()
            if (p.exitValue() != 0) null else (out + err).trim()
        } catch (e: Exception) {
            LogBuffer.log(TAG, "execSu error: ${e.message}")
            null
        }
    }
}
