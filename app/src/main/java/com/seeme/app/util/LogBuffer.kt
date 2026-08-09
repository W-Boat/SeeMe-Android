package com.seeme.app.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/** 内存环形日志缓冲（借鉴 SleepyXposed 的日志系统） */
object LogBuffer {
    private const val TAG = "SeeMe"
    private const val MAX = 300
    private val buffer = ConcurrentLinkedDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Synchronized
    fun log(tag: String, msg: String) {
        val line = "[${fmt.format(Date())}] $tag: $msg"
        buffer.addFirst(line)
        while (buffer.size > MAX) buffer.pollLast()
        Log.i("$TAG/$tag", msg)
    }

    @Synchronized
    fun dump(): String = buffer.joinToString("\n")

    @Synchronized
    fun clear() = buffer.clear()
}
