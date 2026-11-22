package com.aerocell.stand.update

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Журнал обновлений на планшете: переживает перезапуск и установку новой версии.
 * Его видно в служебном меню, а целиком он лежит в files/update-log.txt.
 * Дублируется в logcat с тегом AerocellUpdate.
 */
object UpdateLog {
    private const val TAG = "AerocellUpdate"
    private const val FILE_NAME = "update-log.txt"
    private const val MAX_LINES = 300
    private const val KEEP_LINES = 200

    private val lock = Any()
    private var file: File? = null
    private val format = SimpleDateFormat("dd.MM HH:mm:ss", Locale("ru"))

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    /** Строки журнала, старые сверху. */
    val lines: StateFlow<List<String>> = _lines

    fun init(context: Context) {
        synchronized(lock) {
            if (file != null) return
            val f = File(context.applicationContext.filesDir, FILE_NAME)
            file = f
            _lines.value = try {
                if (f.isFile) f.readLines(Charsets.UTF_8).takeLast(KEEP_LINES) else emptyList()
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

    fun add(message: String) {
        Log.i(TAG, message)
        val line = "${format.format(Date())}  $message"
        synchronized(lock) {
            var next = _lines.value + line
            val trimmed = next.size > MAX_LINES
            if (trimmed) next = next.takeLast(KEEP_LINES)
            _lines.value = next
            val f = file ?: return
            try {
                if (trimmed) {
                    f.writeText(next.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
                } else {
                    f.appendText(line + "\n", Charsets.UTF_8)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "update log write failed", t)
            }
        }
    }

    fun error(message: String, t: Throwable? = null) {
        val detail = t?.let { ": ${it.javaClass.simpleName}${it.message?.let { m -> " $m" } ?: ""}" } ?: ""
        add("ОШИБКА $message$detail")
    }
}
