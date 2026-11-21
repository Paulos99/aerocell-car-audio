package com.aerocell.stand.upload

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class CloudSession(
    val sessionId: String,
    val token: String,
    val pageUrl: String
)

data class CloudStatus(
    val status: String,
    val filename: String? = null,
    val extension: String? = null,
    val size: Long? = null
)

interface CloudUploadApi {
    fun createSession(): CloudSession
    fun status(sessionId: String): CloudStatus
    fun downloadTo(sessionId: String, dest: File)
    fun deleteSession(sessionId: String)
}

class CloudUploadApiClient(
    private val baseUrl: String,
    private val standSecret: String,
    private val client: OkHttpClient = defaultClient()
) : CloudUploadApi {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val root = baseUrl.trimEnd('/')

    override fun createSession(): CloudSession {
        val body = request("POST", "?action=session")
        if (!body.optBoolean("ok", false)) {
            throw IOException("session rejected")
        }
        val sessionId = body.optString("sessionId")
        val token = body.optString("token")
        val pageUrl = body.optString("pageUrl")
        if (sessionId.isBlank() || token.isBlank() || pageUrl.isBlank()) {
            throw IOException("session response incomplete")
        }
        return CloudSession(sessionId, token, pageUrl)
    }

    override fun status(sessionId: String): CloudStatus {
        val body = request("GET", "?action=status&session=${enc(sessionId)}")
        if (body.optString("error") == "missing") {
            return CloudStatus(status = "missing")
        }
        return CloudStatus(
            status = body.optString("status", "waiting"),
            filename = body.optString("filename").ifBlank { null },
            extension = body.optString("extension").ifBlank { null },
            size = if (body.has("size") && !body.isNull("size")) body.optLong("size") else null
        )
    }

    override fun downloadTo(sessionId: String, dest: File) {
        val body = request("GET", "?action=download-url&session=${enc(sessionId)}")
        if (!body.optBoolean("ok", false)) {
            throw IOException("download url rejected")
        }
        val url = body.optString("url")
        if (url.isBlank()) throw IOException("download url missing")
        val get = Request.Builder().url(url).get().build()
        client.newCall(get).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("download failed: ${response.code}")
            }
            val bytes = response.body?.bytes() ?: throw IOException("empty download")
            dest.parentFile?.mkdirs()
            dest.writeBytes(bytes)
        }
    }

    override fun deleteSession(sessionId: String) {
        runCatching { request("POST", "?action=delete&session=${enc(sessionId)}") }
    }

    private fun request(method: String, pathAndQuery: String): JSONObject {
        val builder = Request.Builder()
            .url("$root$pathAndQuery")
            .header("X-Stand-Secret", standSecret)
        if (method == "POST") {
            builder.post("{}".toRequestBody(jsonType))
        } else {
            builder.get()
        }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) {
                throw IOException("stand auth failed")
            }
            if (text.isBlank()) {
                if (!response.isSuccessful) throw IOException("http ${response.code}")
                return JSONObject()
            }
            val json = runCatching { JSONObject(text) }.getOrElse {
                throw IOException("bad json (${response.code})")
            }
            if (!response.isSuccessful && response.code != 404) {
                throw IOException("http ${response.code}")
            }
            return json
        }
    }

    private fun enc(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
