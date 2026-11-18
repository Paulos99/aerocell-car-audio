package com.aerocell.stand.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentManifestParserTest {
    @Test
    fun parsesFullManifest() {
        val json = """
            {
              "manifestVersion": 1,
              "contentVersion": 12,
              "publishedAt": "2026-11-18T15:00:00+03:00",
              "minAppVersionCode": 2,
              "apk": {
                "applicationId": "com.aerocell.stand",
                "versionCode": 2,
                "versionName": "1.1.0",
                "url": "https://storage.yandexcloud.net/aerocell-stand/apk/AEROCELL-1.1.0.apk",
                "sha256": "ABCDEF",
                "size": 18500000
              },
              "files": [
                {
                  "id": "track-club",
                  "type": "audio",
                  "role": "track",
                  "genre": "CLUB",
                  "title": "Alright",
                  "url": "https://example/club.mp3",
                  "sha256": "aabbcc",
                  "size": 100
                }
              ],
              "config": { "location": "sparta" }
            }
        """.trimIndent()

        val manifest = ContentManifestParser.parse(json)
        assertEquals(1, manifest.manifestVersion)
        assertEquals(12, manifest.contentVersion)
        assertEquals("2026-11-18T15:00:00+03:00", manifest.publishedAt)
        assertEquals(2, manifest.minAppVersionCode)
        val apk = manifest.apk!!
        assertEquals("com.aerocell.stand", apk.applicationId)
        assertEquals(2, apk.versionCode)
        assertEquals("1.1.0", apk.versionName)
        assertEquals("abcdef", apk.sha256)
        assertEquals(18500000L, apk.size)
        assertEquals(1, manifest.files.size)
        assertEquals("track-club", manifest.files[0].id)
        assertEquals("CLUB", manifest.files[0].genre)
        assertEquals("track", manifest.files[0].role)
        assertEquals("aabbcc", manifest.files[0].sha256)
        assertEquals("sparta", manifest.config?.location)
        assertTrue(manifest.config?.rawJson?.contains("sparta") == true)
    }

    @Test
    fun missingApkAndConfigAreNull() {
        val json = """
            {
              "contentVersion": 1,
              "files": []
            }
        """.trimIndent()
        val manifest = ContentManifestParser.parse(json)
        assertEquals(1, manifest.manifestVersion)
        assertEquals(1, manifest.contentVersion)
        assertEquals(1, manifest.minAppVersionCode)
        assertNull(manifest.apk)
        assertNull(manifest.config)
        assertTrue(manifest.files.isEmpty())
    }

    @Test(expected = Exception::class)
    fun rejectsInvalidJson() {
        ContentManifestParser.parse("{not-json")
    }
}
