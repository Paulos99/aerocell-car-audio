package com.aerocell.stand.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainAllowlistTest {
    private val allowed = LinksConfig().allowedDomains()

    @Test
    fun defaultsAllowStpSites() {
        assertTrue(DomainAllowlist.isAllowed("https://stp-russia.ru/", allowed))
        assertTrue(DomainAllowlist.isAllowed("https://stp-russia.ru/calculation/", allowed))
        assertTrue(DomainAllowlist.isAllowed("http://stplus.ru/", allowed))
        assertTrue(DomainAllowlist.isAllowed("https://STPLUS.RU/catalog?x=1#y", allowed))
    }

    @Test
    fun subdomainsAreAllowed() {
        assertTrue(DomainAllowlist.isAllowed("https://www.stp-russia.ru/aerocell/", allowed))
        assertTrue(DomainAllowlist.isAllowed("https://shop.stplus.ru/", allowed))
        assertTrue(DomainAllowlist.isAllowed("https://stp-russia.ru./", allowed))
    }

    @Test
    fun lookalikeHostsAreBlocked() {
        assertFalse(DomainAllowlist.isAllowed("https://evilstplus.ru/", allowed))
        assertFalse(DomainAllowlist.isAllowed("https://stplus.ru.evil.com/", allowed))
        assertFalse(DomainAllowlist.isAllowed("https://stp-russia.ru@evil.com/", allowed))
        assertFalse(DomainAllowlist.isAllowed("https://google.com/?q=stplus.ru", allowed))
        assertFalse(DomainAllowlist.isAllowed("https://vk.com/stp", allowed))
    }

    @Test
    fun nonWebSchemesAreBlocked() {
        listOf(
            "tel:+78007757501",
            "mailto:info@stp-russia.ru",
            "intent://stp-russia.ru#Intent;scheme=https;end",
            "market://details?id=ru.stp",
            "file:///sdcard/Download/a.apk",
            "content://downloads/1",
            "javascript:alert(1)",
            "whatsapp://send?text=hi",
            "about:blank",
            "",
            "not a url"
        ).forEach { assertFalse(it, DomainAllowlist.isAllowed(it, allowed)) }
        assertFalse(DomainAllowlist.isAllowed(null, allowed))
    }

    @Test
    fun calculatorDomainIsAddedFromConfig() {
        val links = LinksConfig(calculator = WebLink("https://calc.example-stp.com/aerocell", "Калькулятор"))
        val domains = links.allowedDomains()
        assertTrue(DomainAllowlist.isAllowed("https://calc.example-stp.com/step2", domains))
        assertTrue(DomainAllowlist.isAllowed("https://stp-russia.ru/", domains))
        assertFalse(DomainAllowlist.isAllowed("https://example-stp.com/", domains))
    }

    @Test
    fun hostAndDomainNormalization() {
        assertEquals("stp-russia.ru", DomainAllowlist.hostOf("HTTPS://Stp-Russia.RU/calculation/"))
        assertNull(DomainAllowlist.hostOf("ftp://stp-russia.ru/"))
        assertEquals("stplus.ru", DomainAllowlist.normalizeDomain("*.StPlus.ru."))
        assertNull(DomainAllowlist.normalizeDomain("stp russia.ru"))
        assertNull(DomainAllowlist.normalizeDomain(""))
    }
}
