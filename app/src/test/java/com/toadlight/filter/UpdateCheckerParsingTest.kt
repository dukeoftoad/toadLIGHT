package com.toadlight.filter

import com.toadlight.filter.data.GithubReleaseChecker
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Parsing of the real GitHub releases/latest payload shape with org.json (the
 * Android platform implementation, provided on the JVM test classpath by the
 * org.json test dependency). Runs against realistic raw strings shaped like the
 * toadLIGHT release payloads — every tag ships a single APK asset named
 * `app-release.apk` (attached by the release CI) — with no network involved.
 * Ported from toadFILES (UpdateCheckerParsingTest), minus the edition split
 * toadFILES needs: toadLIGHT has exactly one APK per release, so the pick is
 * simply the first `.apk` asset.
 */
class UpdateCheckerParsingTest {

    private val payload = """
        {
          "url": "https://api.github.com/repos/dukeoftoad/toadLIGHT/releases/17322461",
          "html_url": "https://github.com/dukeoftoad/toadLIGHT/releases/tag/v1.5.1",
          "tag_name": "v1.5.1",
          "target_commitish": "main",
          "name": "v1.5.1",
          "body": "### Fixes\r\n- In-app update flow, ported from toadFILES\r\n- Calmer AMOLED tiers\r\n",
          "draft": false,
          "prerelease": false,
          "created_at": "2026-02-01T10:00:00Z",
          "published_at": "2026-02-01T10:12:00Z",
          "assets": [
            {
              "name": "toadLIGHT-v1.5.1-sources.zip",
              "content_type": "application/zip",
              "browser_download_url": "https://github.com/dukeoftoad/toadLIGHT/releases/download/v1.5.1/toadLIGHT-v1.5.1-sources.zip",
              "size": 204800
            },
            {
              "name": "app-release.apk",
              "content_type": "application/vnd.android.package-archive",
              "browser_download_url": "https://github.com/dukeoftoad/toadLIGHT/releases/download/v1.5.1/app-release.apk",
              "size": 2937200
            },
            {
              "name": "toadLIGHT-v1.5.1-sha256.txt",
              "content_type": "text/plain",
              "browser_download_url": "https://github.com/dukeoftoad/toadLIGHT/releases/download/v1.5.1/toadLIGHT-v1.5.1-sha256.txt",
              "size": 142
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parseRelease picks the first apk asset and the release fields`() {
        val release = GithubReleaseChecker.parseRelease(payload)

        assertEquals("v1.5.1", release.tagName)
        assertEquals("https://github.com/dukeoftoad/toadLIGHT/releases/tag/v1.5.1", release.htmlUrl)
        // The zip and checksum assets must be skipped in favor of the .apk one.
        assertEquals("app-release.apk", release.apkName)
        assertEquals(
            "https://github.com/dukeoftoad/toadLIGHT/releases/download/v1.5.1/app-release.apk",
            release.apkUrl,
        )
        assertEquals(2937200L, release.apkSizeBytes)
        // Notes keep the body text (CR-LF normalized to LF), markdown rendered as plain text.
        assertTrue(release.notes!!.startsWith("### Fixes"))
        assertTrue(release.notes!!.contains("ported from toadFILES"))
        assertTrue(!release.notes!!.contains("\r"))
    }

    @Test
    fun `parseRelease tolerates a release with no apk asset`() {
        val noApk = """
            {
              "tag_name": "v1.6.0",
              "html_url": "https://github.com/dukeoftoad/toadLIGHT/releases/tag/v1.6.0",
              "body": "Sources only.",
              "assets": [
                { "name": "sources.zip", "browser_download_url": "https://x/s.zip", "size": 1 }
              ]
            }
        """.trimIndent()

        val release = GithubReleaseChecker.parseRelease(noApk)

        assertEquals("v1.6.0", release.tagName)
        assertNull(release.apkName)
        assertNull(release.apkUrl)
        assertEquals(0L, release.apkSizeBytes)
    }

    @Test
    fun `apk suffix match is case-insensitive`() {
        val upperApk = """
            {
              "tag_name": "v1.6.0",
              "assets": [
                { "name": "sources.zip", "browser_download_url": "https://x/s.zip", "size": 1 },
                { "name": "App-Release.APK", "browser_download_url": "https://x/a.apk", "size": 2000000 }
              ]
            }
        """.trimIndent()

        val release = GithubReleaseChecker.parseRelease(upperApk)

        assertEquals("App-Release.APK", release.apkName)
        assertEquals("https://x/a.apk", release.apkUrl)
        assertEquals(2000000L, release.apkSizeBytes)
    }

    @Test
    fun `parseRelease treats a missing assets array and empty body honestly`() {
        val minimal = """{"tag_name": "v1.6.1"}"""

        val release = GithubReleaseChecker.parseRelease(minimal)

        assertEquals("v1.6.1", release.tagName)
        assertNull(release.apkUrl)
        assertNull(release.notes)
        // html_url falls back to the repo releases page so "View on GitHub" always works.
        assertEquals("https://github.com/dukeoftoad/toadLIGHT/releases", release.htmlUrl)
    }

    @Test
    fun `parseRelease refuses a payload without tag_name`() {
        try {
            GithubReleaseChecker.parseRelease("""{"html_url": "https://github.com/x"}""")
            fail("expected JSONException for a payload without tag_name")
        } catch (_: JSONException) {
            // expected — the ViewModel surfaces this as an honest Error state
        }
    }
}
