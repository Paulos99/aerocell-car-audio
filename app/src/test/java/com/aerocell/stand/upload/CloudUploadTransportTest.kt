package com.aerocell.stand.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class CloudUploadTransportTest {
    private val tempDir: File = createTempDir(prefix = "aerocell-cloud-test")

    @Test
    fun currentLinkReturnsCloudPage() {
        val api = FakeApi()
        val transport = transport(api)
        val link = transport.currentLink("ignored-local-token")
        assertEquals("https://example.test/u/index.html?t=tok1", link.pageUrl)
        assertTrue(link.available)
        assertFalse(link.failed)
        assertEquals(1, api.sessionsCreated.get())
    }

    @Test
    fun currentLinkReusesWaitingSession() {
        val api = FakeApi()
        val transport = transport(api)
        val first = transport.currentLink("a")
        val second = transport.currentLink("b")
        assertEquals(first.pageUrl, second.pageUrl)
        assertEquals(1, api.sessionsCreated.get())
    }

    @Test
    fun currentLinkFailedWhenSessionRejected() {
        val api = FakeApi(failCreate = true)
        val transport = transport(api)
        val link = transport.currentLink("tok")
        assertNull(link.pageUrl)
        assertFalse(link.available)
        assertTrue(link.failed)
    }

    @Test
    fun tickWhenReadyDownloadsAndAccepts() {
        val api = FakeApi()
        val transport = transport(api)
        val accepted = mutableListOf<Triple<File, String, String>>()
        var started = 0
        var finished = 0
        transport.start(object : VisitorUploadTransport.Listener {
            override fun onUploadStarted() { started += 1 }
            override fun onAccepted(tempFile: File, filename: String, extension: String) {
                accepted += Triple(tempFile, filename, extension)
            }
            override fun onUploadFinished() { finished += 1 }
        })
        transport.currentLink("tok")
        transport.onModalChanged(true)
        api.ready = true
        api.filename = "song.mp3"
        api.extension = "mp3"
        api.payload = "ID3-test".toByteArray()
        transport.tick()
        assertEquals(1, started)
        assertEquals(1, finished)
        assertEquals(1, accepted.size)
        assertEquals("song.mp3", accepted[0].second)
        assertEquals("mp3", accepted[0].third)
        assertTrue(accepted[0].first.exists())
        assertEquals("ID3-test", accepted[0].first.readText())
        assertEquals(1, api.deleted.get())
    }

    @Test
    fun tickDoesNothingWhileModalClosed() {
        val api = FakeApi()
        val transport = transport(api)
        val accepted = mutableListOf<String>()
        transport.start(object : VisitorUploadTransport.Listener {
            override fun onUploadStarted() {}
            override fun onAccepted(tempFile: File, filename: String, extension: String) {
                accepted += filename
            }
            override fun onUploadFinished() {}
        })
        transport.currentLink("tok")
        api.ready = true
        api.filename = "song.mp3"
        api.extension = "mp3"
        transport.tick()
        assertTrue(accepted.isEmpty())
        assertEquals(0, api.statusCalls.get())
    }

    @Test
    fun stopDeletesSession() {
        val api = FakeApi()
        val transport = transport(api)
        transport.start(object : VisitorUploadTransport.Listener {
            override fun onUploadStarted() {}
            override fun onAccepted(tempFile: File, filename: String, extension: String) {}
            override fun onUploadFinished() {}
        })
        transport.currentLink("tok")
        transport.stop()
        assertEquals(1, api.deleted.get())
    }

    @Test
    fun afterAcceptCurrentLinkOpensNewSession() {
        val api = FakeApi()
        val transport = transport(api)
        transport.start(object : VisitorUploadTransport.Listener {
            override fun onUploadStarted() {}
            override fun onAccepted(tempFile: File, filename: String, extension: String) {}
            override fun onUploadFinished() {}
        })
        val first = transport.currentLink("a")
        transport.onModalChanged(true)
        api.ready = true
        api.filename = "song.mp3"
        api.extension = "mp3"
        transport.tick()
        api.ready = false
        val second = transport.currentLink("b")
        assertEquals(2, api.sessionsCreated.get())
        assertTrue(second.pageUrl != first.pageUrl)
        assertNotNull(second.pageUrl)
    }

    @Test
    fun tickIgnoresWaitingStatus() {
        val api = FakeApi()
        val transport = transport(api)
        var started = 0
        transport.start(object : VisitorUploadTransport.Listener {
            override fun onUploadStarted() { started += 1 }
            override fun onAccepted(tempFile: File, filename: String, extension: String) {}
            override fun onUploadFinished() {}
        })
        transport.currentLink("tok")
        transport.onModalChanged(true)
        transport.tick()
        assertEquals(0, started)
        assertEquals(1, api.statusCalls.get())
    }

    private fun transport(api: FakeApi) = CloudUploadTransport(
        tempDir = tempDir,
        api = api,
        autoSchedule = false
    )

    private class FakeApi(
        private val failCreate: Boolean = false
    ) : CloudUploadApi {
        val sessionsCreated = AtomicInteger(0)
        val statusCalls = AtomicInteger(0)
        val deleted = AtomicInteger(0)
        var ready = false
        var filename: String? = null
        var extension: String? = null
        var payload: ByteArray = byteArrayOf(1, 2, 3)

        override fun createSession(): CloudSession {
            if (failCreate) throw IOException("nope")
            val n = sessionsCreated.incrementAndGet()
            return CloudSession(
                sessionId = "sess$n",
                token = "tok$n",
                pageUrl = "https://example.test/u/index.html?t=tok$n"
            )
        }

        override fun status(sessionId: String): CloudStatus {
            statusCalls.incrementAndGet()
            return if (ready) {
                CloudStatus(status = "ready", filename = filename, extension = extension, size = payload.size.toLong())
            } else {
                CloudStatus(status = "waiting")
            }
        }

        override fun downloadTo(sessionId: String, dest: File) {
            dest.parentFile?.mkdirs()
            dest.writeBytes(payload)
        }

        override fun deleteSession(sessionId: String) {
            deleted.incrementAndGet()
        }
    }
}
