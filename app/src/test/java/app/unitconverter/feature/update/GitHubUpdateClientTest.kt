package app.unitconverter.feature.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateClientTest {

    @Test
    fun parserExtractsStableReleaseAndAssets() {
        val release = GitHubReleaseParser.parse(sampleJson())

        assertEquals("v2.1.0", release.tagName)
        assertEquals("Unit Converter V2.1.0", release.name)
        assertEquals(false, release.draft)
        assertEquals(false, release.prerelease)
        assertEquals(3, release.assets.size)

        val decision = UpdatePolicy.decide("2.0.0", release)
        assertTrue(decision is UpdateCheckDecision.Available)
        val info = (decision as UpdateCheckDecision.Available).info
        assertEquals("Unit-Converter-V2.1.0.apk", info.apkAsset.name)
        assertEquals("Unit-Converter-V2.1.0.apk.sha256", info.checksumAsset?.name)
    }

    @Test
    fun parserRejectsMalformedJsonAndMissingTag() {
        val malformed = assertThrows(UpdateException::class.java) {
            GitHubReleaseParser.parse("{\"tag_name\":")
        }
        assertEquals(UpdateErrorKind.MALFORMED_METADATA, malformed.kind)

        val missingTag = assertThrows(UpdateException::class.java) {
            GitHubReleaseParser.parse("""{"html_url":"https://github.com/kiriliodas/Unit-Converter-V2/releases/download/v2.1.0/release","assets":[]}""")
        }
        assertEquals(UpdateErrorKind.MALFORMED_METADATA, missingTag.kind)
    }

    @Test
    fun parserPreservesDraftAndPrereleaseFlagsForPolicyRejection() {
        val release = GitHubReleaseParser.parse(
            sampleJson().replace("\"draft\": false", "\"draft\": true")
                .replace("\"prerelease\": false", "\"prerelease\": true")
        )
        assertTrue(release.draft)
        assertTrue(release.prerelease)
        val decision = UpdatePolicy.decide("2.0.0", release)
        assertTrue(decision is UpdateCheckDecision.Rejected)
        assertFalse(decision is UpdateCheckDecision.Available)
    }

    @Test
    fun clientMapsHttpErrorsAndRateLimit() {
        assertClientError(HttpTextResponse(404, "missing"), UpdateErrorKind.NO_RELEASE, 404)
        assertClientError(
            HttpTextResponse(403, "limited", mapOf("x-ratelimit-remaining" to "0")),
            UpdateErrorKind.RATE_LIMIT,
            403
        )
        assertClientError(HttpTextResponse(500, "boom"), UpdateErrorKind.HTTP, 500)
    }

    @Test
    fun clientUsesInjectedTransportAndParsesSuccessfulResponse() {
        var requestedUrl: String? = null
        val transport = UpdateHttpTransport { url, _, _ ->
            requestedUrl = url
            HttpTextResponse(200, sampleJson())
        }
        val release = GitHubUpdateClient(transport, latestReleaseUrl = "https://api.example.invalid/latest")
            .fetchLatestRelease()
        assertEquals("https://api.example.invalid/latest", requestedUrl)
        assertEquals("v2.1.0", release.tagName)
    }

    private fun assertClientError(response: HttpTextResponse, kind: UpdateErrorKind, status: Int) {
        val client = GitHubUpdateClient(UpdateHttpTransport { _, _, _ -> response })
        val error = assertThrows(UpdateException::class.java) { client.fetchLatestRelease() }
        assertEquals(kind, error.kind)
        assertEquals(status, error.httpStatus)
    }

    private fun sampleJson(): String = """
        {
          "tag_name": "v2.1.0",
          "name": "Unit Converter V2.1.0",
          "body": "## Changes\n- Better conversions",
          "html_url": "https://github.com/kiriliodas/Unit-Converter-V2/releases/tag/v2.1.0",
          "published_at": "2026-10-29T12:00:00Z",
          "draft": false,
          "prerelease": false,
          "assets": [
            {
              "id": 11,
              "name": "source.zip",
              "browser_download_url": "https://github.com/kiriliodas/Unit-Converter-V2/releases/download/v2.1.0/source.zip",
              "size": 100,
              "content_type": "application/zip"
            },
            {
              "id": 12,
              "name": "Unit-Converter-V2.1.0.apk",
              "browser_download_url": "https://github.com/kiriliodas/Unit-Converter-V2/releases/download/v2.1.0/Unit-Converter-V2.1.0.apk",
              "size": 8386382,
              "content_type": "application/vnd.android.package-archive"
            },
            {
              "id": 13,
              "name": "Unit-Converter-V2.1.0.apk.sha256",
              "browser_download_url": "https://github.com/kiriliodas/Unit-Converter-V2/releases/download/v2.1.0/Unit-Converter-V2.1.0.apk.sha256",
              "size": 96,
              "content_type": "text/plain"
            }
          ]
        }
    """.trimIndent()
}
