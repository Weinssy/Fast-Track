package com.wein.fasttrack.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun parseVersionParts_handlesPrefixesAndSuffixes() {
        assertEquals(listOf(3, 0, 0), AppUpdateManager.parseVersionParts("3.0.0"))
        assertEquals(listOf(3, 0, 0), AppUpdateManager.parseVersionParts("v3.0.0"))
        assertEquals(listOf(2, 1, 4), AppUpdateManager.parseVersionParts("V2.1.4"))
        assertEquals(listOf(3, 1, 0), AppUpdateManager.parseVersionParts("  v3.1.0-beta.1  "))
        assertEquals(listOf(2, 0, 1), AppUpdateManager.parseVersionParts("2.0.1+build42"))
        assertEquals(listOf(10, 20, 30), AppUpdateManager.parseVersionParts("v10.20.30-rc1+99"))
    }

    @Test
    fun parseVersionParts_returnsNullForInvalidInputs() {
        assertNull(AppUpdateManager.parseVersionParts(""))
        assertNull(AppUpdateManager.parseVersionParts("   "))
        assertNull(AppUpdateManager.parseVersionParts("v"))
        assertNull(AppUpdateManager.parseVersionParts("abc"))
        assertNull(AppUpdateManager.parseVersionParts("1..2"))
        assertNull(AppUpdateManager.parseVersionParts("1.2."))
        assertNull(AppUpdateManager.parseVersionParts("-1.0.0"))
        assertNull(AppUpdateManager.parseVersionParts("v1.a.0"))
    }

    @Test
    fun isNewerVersion_comparesSemanticVersionsCorrectly() {
        assertTrue(AppUpdateManager.isNewerVersion("2.0.0", "3.0.0"))
        assertTrue(AppUpdateManager.isNewerVersion("v2.0.0", "v2.0.1"))
        assertTrue(AppUpdateManager.isNewerVersion("2.0", "2.0.1"))
        assertTrue(AppUpdateManager.isNewerVersion("3.0.0", "v3.1.0-beta.1"))

        assertFalse(AppUpdateManager.isNewerVersion("3.0.0", "3.0.0"))
        assertFalse(AppUpdateManager.isNewerVersion("3.0.0", "v3.0.0"))
        assertFalse(AppUpdateManager.isNewerVersion("2.0.0", "2.0"))
        assertFalse(AppUpdateManager.isNewerVersion("3.0.0", "3.0.0-rc1"))
        assertFalse(AppUpdateManager.isNewerVersion("3.0.0", "2.9.9"))
        assertFalse(AppUpdateManager.isNewerVersion("3.0.0", "invalid"))
        assertFalse(AppUpdateManager.isNewerVersion("invalid", "3.0.0"))
    }

    @Test
    fun parseLatestReleaseJson_picksFirstApkAssetAndExtractsFields() {
        val json = """
            {
              "tag_name": "v3.1.0",
              "name": "Fast Track v3.1.0",
              "body": "\u2022 Fitur Split Bill\n\u2022 Perbaikan \"tema\" terang",
              "html_url": "https://github.com/Weinssy/Fast-Track/releases/tag/v3.1.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "checksums.sha256",
                  "size": 128,
                  "browser_download_url": "https://github.com/Weinssy/Fast-Track/releases/download/v3.1.0/checksums.sha256"
                },
                {
                  "name": "fast-track-v3.1.0.apk",
                  "size": 9437184,
                  "browser_download_url": "https://github.com/Weinssy/Fast-Track/releases/download/v3.1.0/fast-track-v3.1.0.apk"
                },
                {
                  "name": "fast-track-v3.1.0-arm64.apk",
                  "size": 6291456,
                  "browser_download_url": "https://github.com/Weinssy/Fast-Track/releases/download/v3.1.0/fast-track-v3.1.0-arm64.apk"
                }
              ]
            }
        """.trimIndent()

        val release = AppUpdateManager.parseLatestReleaseJson(json)
        assertNotNull(release)
        release!!
        assertEquals("v3.1.0", release.tagName)
        assertEquals("3.1.0", release.versionName)
        assertEquals("Fast Track v3.1.0", release.releaseName)
        assertEquals("• Fitur Split Bill\n• Perbaikan \"tema\" terang", release.releaseNotes)
        assertEquals("https://github.com/Weinssy/Fast-Track/releases/tag/v3.1.0", release.htmlUrl)
        assertEquals(
            "https://github.com/Weinssy/Fast-Track/releases/download/v3.1.0/fast-track-v3.1.0.apk",
            release.apkDownloadUrl
        )
        assertEquals("fast-track-v3.1.0.apk", release.apkFileName)
        assertEquals(9437184L, release.apkSizeBytes)
    }

    @Test
    fun parseLatestReleaseJson_handlesMissingApkAssetsAndNullFields() {
        val json = """
            {
              "tag_name": "3.2.0",
              "name": null,
              "body": null,
              "html_url": "https://github.com/Weinssy/Fast-Track/releases/tag/3.2.0",
              "assets": []
            }
        """.trimIndent()

        val release = AppUpdateManager.parseLatestReleaseJson(json)
        assertNotNull(release)
        release!!
        assertEquals("3.2.0", release.tagName)
        assertEquals("3.2.0", release.versionName)
        assertEquals("3.2.0", release.releaseName)
        assertEquals("", release.releaseNotes)
        assertNull(release.apkDownloadUrl)
        assertNull(release.apkFileName)
        assertEquals(0L, release.apkSizeBytes)
    }

    @Test
    fun parseLatestReleaseJson_handlesUppercaseApkExtension() {
        val json = """
            {
              "tag_name": "V3.0.1",
              "name": "",
              "body": "Hotfix",
              "html_url": "https://example.com/release",
              "assets": [
                {
                  "name": "FastTrack.APK",
                  "size": 1024,
                  "browser_download_url": "https://example.com/FastTrack.APK"
                }
              ]
            }
        """.trimIndent()

        val release = AppUpdateManager.parseLatestReleaseJson(json)
        assertNotNull(release)
        release!!
        assertEquals("3.0.1", release.versionName)
        assertEquals("V3.0.1", release.releaseName)
        assertEquals("FastTrack.APK", release.apkFileName)
        assertEquals("https://example.com/FastTrack.APK", release.apkDownloadUrl)
        assertEquals(1024L, release.apkSizeBytes)
    }

    @Test
    fun parseLatestReleaseJson_returnsNullOnInvalidOrMissingTag() {
        assertNull(AppUpdateManager.parseLatestReleaseJson(""))
        assertNull(AppUpdateManager.parseLatestReleaseJson("{not valid json}"))
        assertNull(AppUpdateManager.parseLatestReleaseJson("[]"))
        assertNull(AppUpdateManager.parseLatestReleaseJson("""{"name": "No Tag"}"""))
        assertNull(AppUpdateManager.parseLatestReleaseJson("""{"tag_name": "   "}"""))
    }
}
