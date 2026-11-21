package com.aerocell.stand.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalHttpUploadTransportTest {
    @Test
    fun linkUsesLanAddressAndToken() {
        val transport = LocalHttpUploadTransport(
            tempDir = File("."),
            currentToken = { "tok" },
            findAddress = { "192.168.1.8" }
        )
        val link = transport.currentLink("abc123")
        assertEquals("http://192.168.1.8:8733/u/abc123", link.pageUrl)
        assertFalse(link.failed)
        // server is not started in this test, so the URL is a preview only
        assertFalse(link.available)
    }

    @Test
    fun linkEmptyWithoutAddress() {
        val transport = LocalHttpUploadTransport(
            tempDir = File("."),
            currentToken = { "tok" },
            findAddress = { null }
        )
        val link = transport.currentLink("abc123")
        assertNull(link.pageUrl)
        assertFalse(link.available)
        assertFalse(link.failed)
    }
}
