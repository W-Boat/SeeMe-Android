package com.seeme.app.reporter

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 上报器：向 Workers 后端 POST /api/report */
class Reporter(
    private val serverUrl: String,
    private val token: String,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /**
     * 上报设备状态。字段为 null 时不上报（增量更新）。
     * onResult 在后台线程回调。
     */
    fun report(
        deviceId: String,
        deviceName: String,
        batteryLevel: Int?,
        charging: Boolean?,
        foregroundApp: String?,
        foregroundActivity: String?,
        inputState: String?,
        onResult: (Boolean) -> Unit = {},
    ) {
        val body = JSONObject().apply {
            put("deviceId", deviceId)
            put("deviceName", deviceName)
            put("platform", "android")
            if (batteryLevel != null) {
                put("battery", JSONObject().apply {
                    put("level", batteryLevel)
                    if (charging != null) put("charging", charging)
                })
            }
            if (foregroundApp != null) put("foregroundApp", foregroundApp)
            if (foregroundActivity != null) put("foregroundActivity", foregroundActivity)
            if (inputState != null) put("inputState", inputState)
        }.toString()

        val request = Request.Builder()
            .url("$serverUrl/api/report")
            .post(body.toRequestBody(jsonMedia))
            .header("X-Auth-Token", token)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(false)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { onResult(it.isSuccessful) }
            }
        })
    }
}
