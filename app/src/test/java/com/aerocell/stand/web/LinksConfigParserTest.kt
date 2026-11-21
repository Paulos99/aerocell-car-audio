package com.aerocell.stand.web

import com.aerocell.stand.update.ContentManifestParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksConfigParserTest {
    private fun parse(config: String?): LinksConfig {
        val cfg = if (config == null) "" else """, "config": $config"""
        val manifest = ContentManifestParser.parse("""{"contentVersion": 5, "files": []$cfg}""")
        return manifest.config?.links ?: LinksConfig()
    }

    @Test
    fun defaultsWithoutConfig() {
        val links = parse(null)
        assertEquals(LinksConfig.DEFAULT_SITE_URL, links.site.url)
        assertEquals("Сайт STP", links.site.label)
        assertEquals(LinksConfig.DEFAULT_CALCULATOR_URL, links.calculator.url)
        assertEquals("Калькулятор", links.calculator.label)
    }

    @Test
    fun defaultsWithoutLinksSection() {
        val links = parse("""{"location": "sparta", "idle": {"timeoutSec": 60}}""")
        assertEquals(LinksConfig(), links)
    }

    @Test
    fun readsObjectsWithLabels() {
        val links = parse(
            """{"links": {
                "site": {"url": "https://stplus.ru/aerocell/", "label": "Сайт"},
                "calculator": {"url": "https://stp-russia.ru/calculation/?tech=aerocell", "label": "Расчёт"}
            }}"""
        )
        assertEquals(WebLink("https://stplus.ru/aerocell/", "Сайт"), links.site)
        assertEquals(WebLink("https://stp-russia.ru/calculation/?tech=aerocell", "Расчёт"), links.calculator)
    }

    @Test
    fun readsPlainStringUrls() {
        val links = parse("""{"links": {"site": "https://stplus.ru/", "calculator": " https://calc.stplus.ru/ "}}""")
        assertEquals(WebLink("https://stplus.ru/", "Сайт STP"), links.site)
        assertEquals(WebLink("https://calc.stplus.ru/", "Калькулятор"), links.calculator)
    }

    @Test
    fun badUrlsFallBackToDefaults() {
        val links = parse(
            """{"links": {
                "site": {"url": "tel:+78007757501", "label": "Позвонить"},
                "calculator": {"url": "", "label": null}
            }}"""
        )
        assertEquals(LinksConfig.DEFAULT_SITE, links.site)
        assertEquals(LinksConfig.DEFAULT_CALCULATOR, links.calculator)
    }

    @Test
    fun blankOrNullLabelKeepsDefaultAndLongLabelIsCut() {
        val links = parse(
            """{"links": {
                "site": {"url": "https://stplus.ru/", "label": "   "},
                "calculator": {"url": "https://stplus.ru/calc", "label": "Очень длинная подпись для кнопки калькулятора"}
            }}"""
        )
        assertEquals("Сайт STP", links.site.label)
        assertEquals(LinksConfig.MAX_LABEL, links.calculator.label.length)
    }

    @Test
    fun extraAllowedDomains() {
        val links = parse("""{"links": {"allowedDomains": ["*.Yandex.ru", "bad domain", "stp-install.ru"]}}""")
        assertEquals(listOf("yandex.ru", "stp-install.ru"), links.extraDomains)
        val domains = links.allowedDomains()
        assertTrue(DomainAllowlist.isAllowed("https://spb.stp-install.ru/cost/", domains))
        assertTrue(DomainAllowlist.isAllowed("https://stp-russia.ru/", domains))
    }

    @Test
    fun rawConfigKeepsLinksForCache() {
        val manifest = ContentManifestParser.parse(
            """{"contentVersion": 5, "files": [], "config": {"links": {"site": "https://stplus.ru/x"}}}"""
        )
        val reparsed = ContentManifestParser.parse(
            """{"contentVersion": 5, "files": [], "config": ${manifest.config!!.rawJson}}"""
        )
        assertEquals("https://stplus.ru/x", reparsed.config!!.links.site.url)
    }
}
