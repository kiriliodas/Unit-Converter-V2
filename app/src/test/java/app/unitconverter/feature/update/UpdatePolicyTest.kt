package app.unitconverter.feature.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    @Test
    fun semanticVersionComparesNumericallyNotLexicographically() {
        assertTrue(SemanticVersion.parseReleaseTag("v2.0.10")!! > SemanticVersion.parseReleaseTag("2.0.9")!!)
        assertTrue(SemanticVersion.parseReleaseTag("2.10.0")!! > SemanticVersion.parseReleaseTag("v2.9.9")!!)
    }

    @Test
    fun releaseTagAcceptsLeadingVAndBuildMetadataButRejectsPrereleaseSuffix() {
        assertEquals(SemanticVersion(2, 0, 1), SemanticVersion.parseReleaseTag("v2.0.1+build.5"))
        assertNull(SemanticVersion.parseReleaseTag("v2.0.1-beta.1"))
        assertNull(SemanticVersion.parseReleaseTag("2.0.1-rc"))
        assertNull(SemanticVersion.parseReleaseTag("2.0.1-dev"))
    }

    @Test
    fun installedVersionUsesNumericCoreForDevelopmentSuffix() {
        assertEquals(SemanticVersion(0, 0, 1), SemanticVersion.parseInstalledVersionName("0.0.1-dev"))
        assertEquals(SemanticVersion(2, 0, 0), SemanticVersion.parseInstalledVersionName("2.0.0"))
    }

    @Test
    fun malformedVersionsFailSafely() {
        assertNull(SemanticVersion.parseReleaseTag("v2.0"))
        assertNull(SemanticVersion.parseReleaseTag("v02.0.1"))
        assertNull(SemanticVersion.parseReleaseTag("release-2.0.1"))
        assertNull(SemanticVersion.parseReleaseTag(""))
    }

    @Test
    fun decideOffersOnlyStrictlyNewerStableRelease() {
        val available = UpdatePolicy.decide("2.0.9", release(tag = "v2.0.10"))
        assertTrue(available is UpdateCheckDecision.Available)
        assertEquals(UpdateCheckDecision.UpToDate, UpdatePolicy.decide("2.0.10", release(tag = "v2.0.10")))
        assertEquals(UpdateCheckDecision.UpToDate, UpdatePolicy.decide("2.0.10", release(tag = "v2.0.9")))
    }

    @Test
    fun decideRejectsDraftsAndPrereleases() {
        assertRejected(UpdatePolicy.decide("2.0.0", release(tag = "v2.1.0", draft = true)), UpdateErrorKind.NOT_STABLE)
        assertRejected(UpdatePolicy.decide("2.0.0", release(tag = "v2.1.0", prerelease = true)), UpdateErrorKind.NOT_STABLE)
        assertRejected(UpdatePolicy.decide("2.0.0", release(tag = "v2.1.0-beta")), UpdateErrorKind.INVALID_VERSION)
    }

    @Test
    fun missingOrAmbiguousApkDoesNotProduceDownloadOffer() {
        assertRejected(
            UpdatePolicy.decide("2.0.0", release(tag = "v2.1.0", assets = listOf(asset("source.zip")))),
            UpdateErrorKind.MISSING_APK
        )
        val ambiguous = listOf(asset("first.apk"), asset("second.apk"))
        assertRejected(
            UpdatePolicy.decide("2.0.0", release(tag = "v2.1.0", assets = ambiguous)),
            UpdateErrorKind.MISSING_APK
        )
    }

    @Test
    fun officialApkNameWinsAndLoneRenamedApkIsAccepted() {
        val official = asset("Unit-Converter-V2.1.0.apk")
        val other = asset("other.apk")
        assertEquals(official, UpdatePolicy.selectApkAsset(listOf(other, official), SemanticVersion(2, 1, 0)))
        assertEquals(other, UpdatePolicy.selectApkAsset(listOf(other), SemanticVersion(2, 1, 0)))
    }

    @Test
    fun checkIntervalIsTwentyFourHoursAndManualBypassesIt() {
        val last = 1_000_000L
        assertFalse(UpdatePolicy.shouldCheck(last + 1_000L, last))
        assertTrue(UpdatePolicy.shouldCheck(last + UpdatePolicy.CHECK_INTERVAL_MILLIS, last))
        assertTrue(UpdatePolicy.shouldCheck(last + 1_000L, last, manual = true))
        assertTrue(UpdatePolicy.shouldCheck(last, 0L))
    }

    @Test
    fun dismissedVersionSuppressesOnlyThatStartupPrompt() {
        val current = availableInfo("v2.1.0")
        val newer = availableInfo("v2.2.0")
        assertFalse(UpdatePolicy.shouldPrompt(current, dismissedTag = "v2.1.0", manual = false))
        assertTrue(UpdatePolicy.shouldPrompt(newer, dismissedTag = "v2.1.0", manual = false))
        assertTrue(UpdatePolicy.shouldPrompt(current, dismissedTag = "v2.1.0", manual = true))
    }

    private fun assertRejected(decision: UpdateCheckDecision, kind: UpdateErrorKind) {
        assertTrue(decision is UpdateCheckDecision.Rejected)
        assertEquals(kind, (decision as UpdateCheckDecision.Rejected).kind)
    }

    private fun availableInfo(tag: String): UpdateReleaseInfo {
        val version = SemanticVersion.parseReleaseTag(tag)!!
        val release = release(tag)
        return UpdateReleaseInfo(release, version, release.assets.first(), null)
    }

    private fun release(
        tag: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: List<ReleaseAsset> = listOf(asset("Unit-Converter-${tag.uppercase()}.apk"))
    ): GitHubRelease = GitHubRelease(
        tagName = tag,
        name = "Unit Converter $tag",
        body = "Notes",
        htmlUrl = "https://github.com/kiriliodas/Unit-Converter-V2/releases/tag/$tag",
        publishedAt = "2026-10-29T00:00:00Z",
        draft = draft,
        prerelease = prerelease,
        assets = assets
    )

    private fun asset(name: String): ReleaseAsset = ReleaseAsset(
        id = name.hashCode().toLong(),
        name = name,
        downloadUrl = "https://github.com/kiriliodas/Unit-Converter-V2/releases/download/v2.1.0/$name",
        sizeBytes = 1234L,
        contentType = "application/octet-stream"
    )
}
