package com.juexin.assistant.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * 通用 HTTP 客户端
 */
object HttpClient {

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    val gson = Gson()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        return suspendCoroutine { cont ->
            val builder = Request.Builder().url(url).get()
            headers.forEach { (k, v) -> builder.addHeader(k, v) }

            client.newCall(builder.build()).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    cont.resumeWithException(e)
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        cont.resume(body)
                    } else {
                        cont.resumeWithException(IOException("HTTP ${response.code}: $body"))
                    }
                }
            })
        }
    }

    suspend fun post(url: String, bodyJson: String, headers: Map<String, String> = emptyMap()): String {
        return suspendCoroutine { cont ->
            val body = bodyJson.toRequestBody(JSON_MEDIA)
            val builder = Request.Builder().url(url).post(body)
            headers.forEach { (k, v) -> builder.addHeader(k, v) }

            client.newCall(builder.build()).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    cont.resumeWithException(e)
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val respBody = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        cont.resume(respBody)
                    } else {
                        cont.resumeWithException(IOException("HTTP ${response.code}: $respBody"))
                    }
                }
            })
        }
    }

    class IOException(message: String) : Exception(message)
}
