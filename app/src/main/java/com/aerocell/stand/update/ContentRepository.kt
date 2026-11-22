package com.aerocell.stand.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.aerocell.stand.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SyncState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val contentVersion: Int? = null,
    val cachedFiles: Int = 0,
    val lastError: String? = null,
    val offline: Boolean = false,
    val noWifi: Boolean = false,
    val updateAvailable: Boolean = false,
    val updateRequired: Boolean = false,
    val updateVersionName: String? = null,
    val updateVersionCode: Int? = null,
    val apkDownloading: Boolean = false,
    val apkProgress: Float? = null,
    val apkInstalling: Boolean = false,
    val needInstallPermission: Boolean = false,
    val statusText: String? = null,
    /** Время последней попытки проверить облако (System.currentTimeMillis). */
    val lastCheckAtMs: Long? = null,
    /** Удалась ли последняя попытка; null — ещё не проверяли. */
    val lastCheckOk: Boolean? = null,
    /** Версия APK, которая сейчас лежит в манифесте облака. */
    val cloudVersionName: String? = null,
    val cloudVersionCode: Int? = null,
    /** Растёт на каждую упавшую установку, чтобы экран мог на неё отреагировать. */
    val installFailures: Int = 0
)

class ContentRepository(private val context: Context) {
    private val contentDir = File(context.filesDir, "content")
    private val mediaDir = File(contentDir, "files")
    private val updatesDir = File(contentDir, "updates")
    private val manifestFile = File(contentDir, "manifest.json")

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state

    private val manifestHttp = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val downloadHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    init {
        contentDir.mkdirs()
        mediaDir.mkdirs()
        updatesDir.mkdirs()
        loadCachedManifest()?.let { applyManifestToState(it, downloading = false) }
    }

    fun loadCachedManifest(): ContentManifest? {
        if (!manifestFile.isFile) return null
        return try {
            ContentManifestParser.parse(manifestFile.readText(Charsets.UTF_8))
        } catch (_: Throwable) {
            null
        }
    }

    fun cachedTrackFile(genre: String): File? {
        val manifest = loadCachedManifest() ?: return null
        val item = manifest.files.firstOrNull { file ->
            file.role.equals("track", ignoreCase = true) &&
                file.genre.equals(genre, ignoreCase = true)
        } ?: return null
        val local = localMediaFile(item)
        return local.takeIf { Sha256.matches(it, item.sha256) }
    }

    /**
     * Ролики для режима простоя в порядке манифеста. Берём только полностью
     * скачанные файлы: sha256 уже проверен при загрузке, здесь сверяем размер,
     * чтобы не хешировать большие видео на главном потоке.
     */
    fun cachedIdleVideos(): List<File> {
        val manifest = loadCachedManifest() ?: return emptyList()
        return manifest.files
            .filter { it.type.equals("video", ignoreCase = true) && it.role.equals("idle", ignoreCase = true) }
            .map { it to localMediaFile(it) }
            .filter { (item, local) -> local.isFile && local.length() > 0 && (item.size <= 0 || local.length() == item.size) }
            .map { it.second }
    }

    fun idleConfig(): IdleConfig = loadCachedManifest()?.config?.idle ?: IdleConfig()

    fun localMediaFile(file: ContentFile): File {
        val ext = extensionFor(file)
        return File(mediaDir, "${file.id}-${file.sha256.take(12)}.$ext")
    }

    fun localApkFile(apk: ApkInfo): File {
        val name = "AEROCELL-${apk.versionName.ifBlank { apk.versionCode.toString() }}.apk"
        return File(updatesDir, name)
    }

    fun ownTrackConfig(): com.aerocell.stand.upload.OwnTrackConfig =
        loadCachedManifest()?.config?.ownTrack ?: com.aerocell.stand.upload.OwnTrackConfig()

    fun linksConfig(): com.aerocell.stand.web.LinksConfig =
        loadCachedManifest()?.config?.links ?: com.aerocell.stand.web.LinksConfig()

    fun updateConfig(): UpdateConfig = loadCachedManifest()?.config?.update ?: UpdateConfig()

    /**
     * Проверяет манифест в облаке. Манифест и APK лёгкие, поэтому идут по любой сети.
     * Тяжёлые медиа (видео простоя, треки) по умолчанию качаются только по безлимитной сети
     * (Wi-Fi); [mediaAnyNetwork] = true снимает это ограничение (ручная проверка из меню).
     *
     * @return true, если манифест удалось получить.
     */
    suspend fun refresh(
        wifiOnly: Boolean = false,
        enqueueWorker: Boolean = true,
        mediaAnyNetwork: Boolean = false
    ): Boolean {
        val cached = loadCachedManifest()
        if (cached != null) applyManifestToState(cached, downloading = false)

        val online = NetworkMonitor.hasInternet(context)
        val wifi = NetworkMonitor.isWifi(context)
        if (!online) {
            UpdateLog.add("проверка облака: нет сети")
            _state.update {
                it.copy(
                    checking = false,
                    offline = true,
                    noWifi = false,
                    lastError = null,
                    statusText = if (cached != null) "Нет сети — работаю из кэша" else "Нет сети",
                    lastCheckAtMs = System.currentTimeMillis(),
                    lastCheckOk = false
                )
            }
            return false
        }
        if (wifiOnly && !wifi) {
            _state.update {
                it.copy(
                    checking = false,
                    offline = false,
                    noWifi = true,
                    lastError = null,
                    statusText = if (cached != null) "Нет Wi-Fi — работаю из кэша" else "Нет Wi-Fi",
                    lastCheckAtMs = System.currentTimeMillis(),
                    lastCheckOk = false
                )
            }
            return false
        }

        _state.update {
            it.copy(checking = true, offline = false, noWifi = false, lastError = null, statusText = "Проверяю обновления…")
        }
        val result = withContext(Dispatchers.IO) { fetchManifestRaw() }
        return result.fold(
            onSuccess = { (manifest, raw) ->
                val apk = manifest.apk
                UpdateLog.add(
                    "проверка облака: в облаке " +
                        (apk?.let { "${it.versionName} (${it.versionCode})" } ?: "нет APK") +
                        ", у нас ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                )
                saveManifest(manifest, raw)
                applyManifestToState(manifest, downloading = false)
                val pending = pendingMedia(manifest)
                _state.update {
                    it.copy(
                        checking = false,
                        lastError = null,
                        statusText = if (pending.isEmpty()) "Контент актуален" else "Обновление контента…",
                        lastCheckAtMs = System.currentTimeMillis(),
                        lastCheckOk = true
                    )
                }
                if (enqueueWorker && pending.isNotEmpty()) {
                    enqueueSync(forceNetwork = mediaAnyNetwork)
                }
                true
            },
            onFailure = { error ->
                UpdateLog.error("проверка облака не удалась", error)
                _state.update {
                    it.copy(
                        checking = false,
                        lastError = error.message,
                        statusText = if (cached != null) "Не удалось проверить — работаю из кэша" else "Не удалось проверить обновления",
                        lastCheckAtMs = System.currentTimeMillis(),
                        lastCheckOk = false
                    )
                }
                false
            }
        )
    }

    fun enqueueSync(forceNetwork: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (forceNetwork) NetworkType.CONNECTED else NetworkType.UNMETERED)
            .build()
        val request = OneTimeWorkRequestBuilder<ContentSyncWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf("forceNetwork" to forceNetwork))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun fetchManifest(): Result<ContentManifest> {
        return fetchManifestRaw().map { it.first }
    }

    fun fetchManifestRaw(): Result<Pair<ContentManifest, String>> {
        return try {
            val request = Request.Builder()
                .url(BuildConfig.CONTENT_MANIFEST_URL)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .build()
            manifestHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IOException("HTTP ${response.code}"))
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return Result.failure(IOException("пустой манифест"))
                Result.success(ContentManifestParser.parse(body) to body)
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun saveManifest(manifest: ContentManifest, raw: String? = null) {
        contentDir.mkdirs()
        val tmp = File(contentDir, "manifest.json.tmp")
        tmp.writeText(raw ?: toJson(manifest), Charsets.UTF_8)
        if (manifestFile.exists()) manifestFile.delete()
        if (!tmp.renameTo(manifestFile)) {
            tmp.copyTo(manifestFile, overwrite = true)
            tmp.delete()
        }
    }

    fun pendingMedia(manifest: ContentManifest): List<ContentFile> {
        return manifest.files.filter { file ->
            !Sha256.matches(localMediaFile(file), file.sha256)
        }
    }

    fun downloadFile(
        url: String,
        dest: File,
        expectedSha: String,
        expectedSize: Long,
        onProgress: (Long) -> Unit = {}
    ): File {
        dest.parentFile?.mkdirs()
        if (Sha256.matches(dest, expectedSha)) return dest
        val part = File(dest.parentFile, dest.name + ".part")
        var offset = if (part.isFile) part.length() else 0L
        if (expectedSize > 0 && offset > expectedSize) {
            part.delete()
            offset = 0L
        }
        val request = Request.Builder().url(url).apply {
            if (offset > 0L) header("Range", "bytes=$offset-")
        }.build()
        downloadHttp.newCall(request).execute().use { response ->
            if (response.code == 416) {
                if (Sha256.matches(part, expectedSha)) {
                    atomicMove(part, dest)
                    return dest
                }
                part.delete()
                throw IOException("HTTP 416 для $url")
            }
            if (!response.isSuccessful && response.code != 206) {
                throw IOException("HTTP ${response.code} для $url")
            }
            val append = response.code == 206 && offset > 0L
            if (!append && part.exists()) {
                part.delete()
                offset = 0L
            }
            val body = response.body ?: throw IOException("пустой ответ")
            FileOutputStream(part, append).use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    var written = if (append) offset else 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        written += n
                        onProgress(written)
                    }
                }
            }
        }
        if (!Sha256.matches(part, expectedSha)) {
            part.delete()
            throw IOException("контрольная сумма не совпала: ${dest.name}")
        }
        atomicMove(part, dest)
        return dest
    }

    fun syncMedia(manifest: ContentManifest) {
        val pending = pendingMedia(manifest)
        if (pending.isEmpty()) {
            cleanupObsolete(manifest)
            _state.update {
                it.copy(downloading = false, statusText = "Контент актуален", cachedFiles = countCached(manifest))
            }
            return
        }
        _state.update {
            it.copy(downloading = true, statusText = "Обновление контента…")
        }
        pending.forEach { file ->
            downloadFile(file.url, localMediaFile(file), file.sha256, file.size)
        }
        cleanupObsolete(manifest)
        _state.update {
            it.copy(
                downloading = false,
                statusText = "Контент актуален",
                cachedFiles = countCached(manifest)
            )
        }
    }

    fun downloadApk(apk: ApkInfo, onProgress: (Long) -> Unit = {}): File {
        _state.update { it.copy(apkDownloading = true, apkProgress = 0f, statusText = "Скачиваю обновление…") }
        return try {
            val dest = localApkFile(apk)
            if (!Sha256.matches(dest, apk.sha256)) UpdateLog.add("скачиваю ${apk.versionName}, ${apk.size / 1_000_000} МБ")
            val file = downloadFile(apk.url, dest, apk.sha256, apk.size) { written ->
                val total = apk.size.takeIf { it > 0 } ?: written
                onProgress(written)
                _state.update { it.copy(apkProgress = (written.toFloat() / total.toFloat()).coerceIn(0f, 1f)) }
            }
            _state.update { it.copy(apkDownloading = false, apkProgress = 1f) }
            UpdateLog.add("${apk.versionName} скачана, sha256 сошёлся")
            file
        } catch (t: Throwable) {
            UpdateLog.error("скачивание ${apk.versionName}", t)
            _state.update {
                it.copy(apkDownloading = false, apkProgress = null, lastError = t.message, statusText = "Не удалось скачать обновление")
            }
            throw t
        }
    }

    fun markInstalling() {
        _state.update { it.copy(apkInstalling = true, statusText = "Установка обновления…") }
    }

    fun clearInstalling() {
        _state.update { it.copy(apkInstalling = false) }
    }

    /** Установка упала (сообщил PackageInstaller или вышел таймаут). */
    fun reportInstallFailed(message: String) {
        _state.update {
            it.copy(
                apkInstalling = false,
                lastError = message,
                statusText = "Не удалось установить обновление",
                installFailures = it.installFailures + 1
            )
        }
    }

    fun markNeedInstallPermission(needed: Boolean) {
        _state.update { it.copy(needInstallPermission = needed) }
    }

    fun reportError(message: String) {
        _state.update { it.copy(lastError = message, checking = false, downloading = false, apkDownloading = false) }
    }

    private fun applyManifestToState(manifest: ContentManifest, downloading: Boolean) {
        val apk = manifest.apk
        val samePackage = apk == null ||
            apk.applicationId.isNullOrBlank() ||
            apk.applicationId == BuildConfig.APPLICATION_ID
        val updateAvailable = samePackage && apk != null && apk.versionCode > BuildConfig.VERSION_CODE
        val updateRequired = updateAvailable && BuildConfig.VERSION_CODE < manifest.minAppVersionCode
        _state.update {
            it.copy(
                contentVersion = manifest.contentVersion,
                cachedFiles = countCached(manifest),
                downloading = downloading,
                updateAvailable = updateAvailable,
                updateRequired = updateRequired,
                updateVersionName = if (updateAvailable) apk?.versionName else it.updateVersionName.takeIf { updateAvailable },
                updateVersionCode = if (updateAvailable) apk?.versionCode else null,
                cloudVersionName = apk?.versionName,
                cloudVersionCode = apk?.versionCode,
                offline = false
            )
        }
    }

    private fun countCached(manifest: ContentManifest): Int {
        return manifest.files.count { Sha256.matches(localMediaFile(it), it.sha256) }
    }

    private fun cleanupObsolete(manifest: ContentManifest) {
        val keep = manifest.files.map { localMediaFile(it).name }.toSet()
        mediaDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".part")) return@forEach
            if (file.name !in keep) file.delete()
        }
    }

    private fun extensionFor(file: ContentFile): String {
        val fromUrl = file.url.substringAfterLast('/').substringAfterLast('.', "")
        val clean = fromUrl.lowercase().substringBefore('?').filter { it.isLetterOrDigit() }
        if (clean.isNotEmpty()) return clean
        return when (file.type.lowercase()) {
            "audio" -> "mp3"
            "video" -> "mp4"
            "apk" -> "apk"
            else -> "bin"
        }
    }

    private fun atomicMove(from: File, to: File) {
        if (to.exists()) to.delete()
        if (!from.renameTo(to)) {
            from.copyTo(to, overwrite = true)
            from.delete()
        }
    }

    private fun toJson(manifest: ContentManifest): String {
        // Persist the last fetched payload shape via a reconstruct sufficient for parse()
        val files = manifest.files.joinToString(",") { file ->
            val title = file.title?.let { ",\"title\":${jsonString(it)}" } ?: ""
            val genre = file.genre?.let { ",\"genre\":${jsonString(it)}" } ?: ""
            """{"id":${jsonString(file.id)},"type":${jsonString(file.type)},"role":${jsonString(file.role)},"url":${jsonString(file.url)},"sha256":${jsonString(file.sha256)},"size":${file.size}$title$genre}"""
        }
        val apk = manifest.apk?.let { apk ->
            val appId = apk.applicationId?.let { ",\"applicationId\":${jsonString(it)}" } ?: ""
            ""","apk":{"versionCode":${apk.versionCode},"versionName":${jsonString(apk.versionName)},"url":${jsonString(apk.url)},"sha256":${jsonString(apk.sha256)},"size":${apk.size}$appId}"""
        } ?: ""
        val config = manifest.config?.rawJson?.let { ",\"config\":$it" } ?: ""
        val published = manifest.publishedAt?.let { ",\"publishedAt\":${jsonString(it)}" } ?: ""
        return """{"manifestVersion":${manifest.manifestVersion},"contentVersion":${manifest.contentVersion}$published,"minAppVersionCode":${manifest.minAppVersionCode}$apk,"files":[$files]$config}"""
    }

    private fun jsonString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        return "\"$escaped\""
    }

    companion object {
        const val UNIQUE_WORK = "aerocell-content-sync"
    }
}
