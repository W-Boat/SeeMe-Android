package com.seeme.app.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seeme.app.SeeMeService

/** 开机自启：系统启动完成后拉起前台采集服务 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            SeeMeService.start(context)
        }
    }
}
