package com.aerocell.stand.web

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** Одна ссылка для окна с сайтом: адрес и подпись на кнопке. */
data class WebLink(val url: String, val label: String)

/** Какую из ссылок открыли: плитки «Калькулятор» и «Каталог» главного меню. */
enum class WebTarget { CATALOG, CALCULATOR }

/**
 * Ссылки «Каталог» и «Калькулятор» из config.links манифеста.
 * Если в манифесте ничего нет или адрес кривой, берём встроенные значения.
 *
 * Формат в манифесте:
 * "links": {
 *   "catalog": {"url": "https://stp-russia.ru/catalog/", "label": "Каталог"},
 *   "calculator": {"url": "https://stp-russia.ru/calculation/", "label": "Калькулятор"},
 *   "allowedDomains": ["stp-russia.ru"]
 * }
 * Вместо объекта можно написать просто строку с адресом.
 * Старый ключ "site" (1.7.0) читается как каталог, если "catalog" не задан.
 */
data class LinksConfig(
    val catalog: WebLink = DEFAULT_CATALOG,
    val calculator: WebLink = DEFAULT_CALCULATOR,
    val extraDomains: List<String> = emptyList()
) {
    fun link(target: WebTarget): WebLink = when (target) {
        WebTarget.CATALOG -> catalog
        WebTarget.CALCULATOR -> calculator
    }

    /** Домены, по которым можно ходить внутри окна (вместе с поддоменами). */
    fun allowedDomains(): Set<String> {
        val out = LinkedHashSet<String>()
        out.addAll(BASE_DOMAINS)
        DomainAllowlist.hostOf(catalog.url)?.let { out.add(it) }
        DomainAllowlist.hostOf(calculator.url)?.let { out.add(it) }
        extraDomains.forEach { d -> DomainAllowlist.normalizeDomain(d)?.let { out.add(it) } }
        return out
    }

    companion object {
        const val DEFAULT_CATALOG_URL = "https://stp-russia.ru/catalog/"
        const val DEFAULT_CALCULATOR_URL = "https://stp-russia.ru/calculation/"
        const val DEFAULT_CATALOG_LABEL = "Каталог"
        const val DEFAULT_CALCULATOR_LABEL = "Калькулятор"
        const val MAX_LABEL = 24

        val DEFAULT_CATALOG = WebLink(DEFAULT_CATALOG_URL, DEFAULT_CATALOG_LABEL)
        val DEFAULT_CALCULATOR = WebLink(DEFAULT_CALCULATOR_URL, DEFAULT_CALCULATOR_LABEL)

        /** Сайты STP: всегда разрешены вместе с поддоменами. */
        val BASE_DOMAINS = listOf("stp-russia.ru", "stplus.ru")

        fun parse(obj: JSONObject?): LinksConfig {
            if (obj == null) return LinksConfig()
            return LinksConfig(
                catalog = parseLink(
                    if (obj.has("catalog") && !obj.isNull("catalog")) obj.opt("catalog") else obj.opt("site"),
                    DEFAULT_CATALOG
                ),
                calculator = parseLink(obj.opt("calculator"), DEFAULT_CALCULATOR),
                extraDomains = parseDomains(obj.optJSONArray("allowedDomains"))
            )
        }

        internal fun parseLink(raw: Any?, fallback: WebLink): WebLink {
            val (url, label) = when (raw) {
                is String -> raw to null
                is JSONObject -> raw.optString("url", "") to raw.optString("label", "")
                else -> return fallback
            }
            val cleanUrl = url.trim()
            if (!DomainAllowlist.isWebUrl(cleanUrl)) return fallback
            val cleanLabel = label?.trim()?.takeIf { it.isNotEmpty() && it != "null" }?.take(MAX_LABEL)
                ?: fallback.label
            return WebLink(cleanUrl, cleanLabel)
        }

        private fun parseDomains(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            val out = ArrayList<String>()
            for (i in 0 until arr.length()) {
                DomainAllowlist.normalizeDomain(arr.optString(i, ""))?.let { out.add(it) }
            }
            return out
        }
    }
}

/** Белый список доменов для окна с сайтом. Чистый Kotlin, чтобы проверять тестами без Android. */
object DomainAllowlist {

    /** http или https и нормальный хост. Всё остальное (tel:, mailto:, intent:, file:, javascript:) — нет. */
    fun isWebUrl(url: String?): Boolean = hostOf(url) != null

    fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = try {
            URI(url.trim())
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        return normalizeDomain(uri.host)
    }

    fun normalizeDomain(raw: String?): String? {
        val d = raw?.trim()?.lowercase()?.trimEnd('.')?.removePrefix("*.")?.removePrefix(".") ?: return null
        if (d.isEmpty() || d.length > 253) return null
        if (!d.all { it.isLetterOrDigit() || it == '-' || it == '.' }) return null
        if (d.startsWith('-') || d.contains("..")) return null
        return d
    }

    /** Адрес можно открыть, если это http(s) и хост совпадает с доменом из списка или его поддоменом. */
    fun isAllowed(url: String?, domains: Collection<String>): Boolean {
        val host = hostOf(url) ?: return false
        return domains.any { raw ->
            val d = normalizeDomain(raw) ?: return@any false
            host == d || host.endsWith(".$d")
        }
    }
}
