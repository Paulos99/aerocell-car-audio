package com.aerocell.stand.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadValidationTest {
    private val okSize = 128_000L

    @Test
    fun acceptsAllowedExtensionsRegardlessOfCase() {
        for (ext in listOf("mp3", "MP3", "m4a", "AAC", "wav", "Flac", "ogg")) {
            val result = UploadValidation.decide(true, "track.$ext", "application/octet-stream", okSize)
            assertTrue("$ext should be accepted", result is UploadDecision.Accept)
            assertEquals(ext.lowercase(), (result as UploadDecision.Accept).extension)
        }
    }

    @Test
    fun acceptsMimeWhenFilenameHasNoExtension() {
        val result = UploadValidation.decide(true, "voice_memo", "audio/mpeg", okSize)
        val accept = result as UploadDecision.Accept
        assertEquals("mp3", accept.extension)
    }

    @Test
    fun rejectsUnknownTypes() {
        for (name in listOf("photo.jpg", "clip.mp4", "note.txt", "track.mp3.exe", "archive.zip")) {
            val result = UploadValidation.decide(true, name, "application/octet-stream", okSize)
            assertEquals(name, UploadReject.BadType, (result as UploadDecision.Reject).reason)
        }
    }

    @Test
    fun rejectsWhenTokenDoesNotMatch() {
        val result = UploadValidation.decide(false, "song.mp3", "audio/mpeg", okSize)
        assertEquals(UploadReject.BadToken, (result as UploadDecision.Reject).reason)
    }

    @Test
    fun rejectsEmptyOrMissingBody() {
        assertEquals(UploadReject.Empty, reject(true, "song.mp3", "audio/mpeg", 0L))
        assertEquals(UploadReject.Empty, reject(true, "song.mp3", "audio/mpeg", -1L))
    }

    @Test
    fun sizeLimitIsInclusive() {
        val max = UploadConfig.MAX_BYTES
        assertTrue(UploadValidation.decide(true, "song.mp3", null, max) is UploadDecision.Accept)
        assertEquals(
            UploadReject.TooLarge,
            reject(true, "song.mp3", "audio/mpeg", max + 1)
        )
    }

    @Test
    fun displayNameUsesBasename() {
        assertEquals("My Mix.mp3", UploadValidation.displayName("/storage/emulated/0/My Mix.mp3"))
        assertEquals("трек", UploadValidation.displayName("  "))
    }

    private fun reject(tokenOk: Boolean, name: String?, mime: String?, size: Long): UploadReject {
        return (UploadValidation.decide(tokenOk, name, mime, size) as UploadDecision.Reject).reason
    }
}
