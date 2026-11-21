package com.aerocell.stand.upload

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.IOException

class UploadServer(
    port: Int,
    private val tempDir: File,
    private val currentToken: () -> String,
    private val onAccepted: (tempFile: File, filename: String, extension: String) -> Unit,
    private val onStarted: () -> Unit = {},
    private val onFinished: () -> Unit = {}
) : NanoHTTPD(port) {

    init {
        tempDir.mkdirs()
        setTempFileManagerFactory { CacheTempFileManager(tempDir) }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.trimEnd('/').ifBlank { "/" }
        val token = tokenFrom(uri) ?: return if (session.method == Method.GET && uri == "/") {
            html(Response.Status.OK, UploadPageHtml.render())
        } else {
            text(Response.Status.NOT_FOUND, "Not found")
        }

        return when (session.method) {
            Method.GET, Method.HEAD -> html(Response.Status.OK, UploadPageHtml.render())
            Method.POST -> handlePost(session, token)
            else -> json(Response.Status.METHOD_NOT_ALLOWED, """{"ok":false,"error":"server"}""")
        }
    }

    private fun handlePost(session: IHTTPSession, token: String): Response {
        onStarted()
        try {
            return handlePostBody(session, token)
        } finally {
            onFinished()
        }
    }

    private fun handlePostBody(session: IHTTPSession, token: String): Response {
        val tokenOk = UploadTokens.matches(currentToken(), token)
        if (!tokenOk) {
            return json(Response.Status.GONE, """{"ok":false,"error":"token"}""")
        }
        val declared = session.headers["content-length"]?.toLongOrNull()
        if (declared != null && declared > UploadConfig.MAX_BYTES + 4096) {
            return json(Response.Status.PAYLOAD_TOO_LARGE, """{"ok":false,"error":"size"}""")
        }

        val files = HashMap<String, String>()
        try {
            session.parseBody(files)
        } catch (e: ResponseException) {
            return json(statusFrom(e.status), """{"ok":false,"error":"server"}""")
        } catch (e: IOException) {
            return json(Response.Status.INTERNAL_ERROR, """{"ok":false,"error":"server"}""")
        }

        val tempPath = files["file"] ?: files.values.firstOrNull()
        val tempFile = tempPath?.let { File(it) }
        val filename = session.parms["file"] ?: session.parms["filename"]
        val mime = guessMime(session, filename)
        val size = when {
            tempFile != null && tempFile.isFile -> tempFile.length()
            else -> 0L
        }
        when (val decision = UploadValidation.decide(true, filename, mime, size)) {
            is UploadDecision.Reject -> {
                tempFile?.delete()
                return reject(decision.reason)
            }
            is UploadDecision.Accept -> {
                if (tempFile == null || !tempFile.isFile) {
                    return json(Response.Status.BAD_REQUEST, """{"ok":false,"error":"empty"}""")
                }
                val kept = File(tempDir, "incoming-${System.nanoTime()}.${decision.extension}")
                if (!tempFile.renameTo(kept)) {
                    tempFile.copyTo(kept, overwrite = true)
                    tempFile.delete()
                }
                try {
                    onAccepted(kept, decision.filename, decision.extension)
                } catch (_: Throwable) {
                    kept.delete()
                    return json(Response.Status.INTERNAL_ERROR, """{"ok":false,"error":"server"}""")
                }
                return json(Response.Status.OK, """{"ok":true,"name":${jsonString(decision.filename)}}""")
            }
        }
    }

    private fun reject(reason: UploadReject): Response = when (reason) {
        UploadReject.BadToken -> json(Response.Status.GONE, """{"ok":false,"error":"token"}""")
        UploadReject.BadType -> json(Response.Status.UNSUPPORTED_MEDIA_TYPE, """{"ok":false,"error":"type"}""")
        UploadReject.TooLarge -> json(Response.Status.PAYLOAD_TOO_LARGE, """{"ok":false,"error":"size"}""")
        UploadReject.Empty -> json(Response.Status.BAD_REQUEST, """{"ok":false,"error":"empty"}""")
    }

    private fun guessMime(session: IHTTPSession, filename: String?): String? {
        return session.headers["content-type"]?.takeIf { !it.contains("multipart/form-data", ignoreCase = true) }
    }

    private fun tokenFrom(uri: String): String? {
        val parts = uri.split('/').filter { it.isNotEmpty() }
        if (parts.size == 2 && parts[0] == "u") {
            val token = parts[1]
            return token.takeIf { UploadTokens.isWellFormed(it) }
        }
        return null
    }

    private fun html(status: Response.Status, body: String): Response =
        newFixedLengthResponse(status, "text/html; charset=utf-8", body).also { noStore(it) }

    private fun json(status: Response.Status, body: String): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body).also { noStore(it) }

    private fun text(status: Response.Status, body: String): Response =
        newFixedLengthResponse(status, "text/plain; charset=utf-8", body)

    private fun noStore(response: Response) {
        response.addHeader("Cache-Control", "no-store")
        response.addHeader("Access-Control-Allow-Origin", "*")
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
        append('"')
    }

    private fun statusFrom(status: Response.Status?): Response.Status =
        status ?: Response.Status.INTERNAL_ERROR

    private class CacheTempFileManager(private val dir: File) : TempFileManager {
        private val created = mutableListOf<File>()

        override fun createTempFile(filename_hint: String?): TempFile {
            dir.mkdirs()
            val file = File.createTempFile("up-", ".part", dir)
            created += file
            return object : TempFile {
                override fun open() = java.io.FileOutputStream(file)
                override fun delete() { file.delete() }
                override fun getName(): String = file.absolutePath
            }
        }

        override fun clear() {
            created.forEach { runCatching { it.delete() } }
            created.clear()
        }
    }
}
