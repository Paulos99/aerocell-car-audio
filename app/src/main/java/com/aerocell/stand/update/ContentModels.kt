package com.aerocell.stand.update

import com.aerocell.stand.kiosk.KioskPolicy
import org.json.JSONArray
import org.json.JSONObject

data class ContentManifest(
    val manifestVersion: Int,
    val contentVersion: Int,
    val publishedAt: String?,
    val minAppVersionCode: Int,
    val apk: ApkInfo?,
    val files: List<ContentFile>,
    val config: ContentConfig?
)

data class ApkInfo(
    val applicationId: String?,
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val sha256: String,
    val size: Long
)

data class ContentFile(
    val id: String,
    val type: String,
    val role: String,
    val title: String?,
    val genre: String?,
    val url: String,
    val sha256: String,
    val size: Long
)

data class ContentConfig(
    val location: String?,
    val rawJson: String?,
    val idle: IdleConfig = IdleConfig(),
    val kiosk: KioskConfig = KioskConfig()
)

/** Настройки режима простоя из config.idle манифеста. */
data class IdleConfig(
    val timeoutSec: Int = DEFAULT_TIMEOUT_SEC,
    val videoVolume: Float = DEFAULT_VIDEO_VOLUME
) {
    companion object {
        const val DEFAULT_TIMEOUT_SEC = 90
        const val DEFAULT_VIDEO_VOLUME = 0.25f
        const val MIN_TIMEOUT_SEC = 10
    }
}

/** Настройки киоска из config.kiosk манифеста. */
data class KioskConfig(
    val pin: String = KioskPolicy.DEFAULT_PIN
)

object ContentManifestParser {
    fun parse(text: String): ContentManifest {
        val obj = JSONObject(text)
        val filesJson = obj.optJSONArray("files") ?: JSONArray()
        val files = ArrayList<ContentFile>(filesJson.length())
        for (i in 0 until filesJson.length()) {
            val f = filesJson.getJSONObject(i)
            files.add(
                ContentFile(
                    id = f.getString("id"),
                    type = f.optString("type", "file"),
                    role = f.optString("role", ""),
                    title = f.optionalString("title"),
                    genre = f.optionalString("genre"),
                    url = f.getString("url"),
                    sha256 = f.getString("sha256").trim().lowercase(),
                    size = f.optLong("size", 0L)
                )
            )
        }
        val apkObj = obj.optJSONObject("apk")
        val apk = if (apkObj == null) {
            null
        } else {
            ApkInfo(
                applicationId = apkObj.optionalString("applicationId"),
                versionCode = apkObj.getInt("versionCode"),
                versionName = apkObj.optString("versionName", ""),
                url = apkObj.getString("url"),
                sha256 = apkObj.getString("sha256").trim().lowercase(),
                size = apkObj.optLong("size", 0L)
            )
        }
        val configObj = obj.optJSONObject("config")
        val config = configObj?.let {
            ContentConfig(
                location = it.optionalString("location"),
                rawJson = it.toString(),
                idle = parseIdle(it.optJSONObject("idle")),
                kiosk = parseKiosk(it.optJSONObject("kiosk"))
            )
        }
        return ContentManifest(
            manifestVersion = obj.optInt("manifestVersion", 1),
            contentVersion = obj.getInt("contentVersion"),
            publishedAt = obj.optionalString("publishedAt"),
            minAppVersionCode = obj.optInt("minAppVersionCode", 1),
            apk = apk,
            files = files,
            config = config
        )
    }

    fun parseIdle(idle: JSONObject?): IdleConfig {
        if (idle == null) return IdleConfig()
        val timeout = idle.optInt("timeoutSec", IdleConfig.DEFAULT_TIMEOUT_SEC)
            .takeIf { it > 0 }
            ?.coerceAtLeast(IdleConfig.MIN_TIMEOUT_SEC)
            ?: IdleConfig.DEFAULT_TIMEOUT_SEC
        val rawVolume = idle.optDouble("videoVolume", IdleConfig.DEFAULT_VIDEO_VOLUME.toDouble())
        val volume = if (rawVolume.isNaN()) IdleConfig.DEFAULT_VIDEO_VOLUME else rawVolume.toFloat().coerceIn(0f, 1f)
        return IdleConfig(timeoutSec = timeout, videoVolume = volume)
    }

    fun parseKiosk(kiosk: JSONObject?): KioskConfig {
        if (kiosk == null) return KioskConfig()
        return KioskConfig(pin = KioskPolicy.resolvePin(kiosk.optString("pin", "")))
    }

    private fun JSONObject.optionalString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = optString(key, "")
        return value.takeIf { it.isNotBlank() }
    }
}
