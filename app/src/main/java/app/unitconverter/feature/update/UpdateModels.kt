package app.unitconverter.feature.update

import java.io.InputStream
import java.security.MessageDigest

/** Numeric semantic version used for stable release tags and installed version names. */
data class SemanticVersion(
    val major: Long,
    val minor: Long,
    val patch: Long
) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int = when {
        major != other.major -> major.compareTo(other.major)
        minor != other.minor -> minor.compareTo(other.minor)
        else -> patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /**
         * Parses a stable GitHub release tag. A leading `v` and SemVer build
         * metadata (`+...`) are accepted. Any hyphenated suffix is treated as
         * a prerelease/dev marker and rejected by the stable channel.
         */
        fun parseReleaseTag(raw: String?): SemanticVersion? {
            val value = normalize(raw) ?: return null
            if ('-' in value) return null
            return parseCore(value.substringBefore('+'))
        }

        /**
         * Parses the installed Android `versionName`. The C8 development
         * build is named `0.0.1-dev`; for an already-installed package it is
         * safe to compare its numeric core while ignoring the suffix.
         */
        fun parseInstalledVersionName(raw: String?): SemanticVersion? {
            val value = normalize(raw) ?: return null
            val core = value.substringBefore('-').substringBefore('+')
            return parseCore(core)
        }

        private fun normalize(raw: String?): String? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            return trimmed.removePrefix("v").removePrefix("V")
        }

        private fun parseCore(core: String): SemanticVersion? {
            val parts = core.split('.')
            if (parts.size != 3) return null
            val values = LongArray(3)
            for ((index, part) in parts.withIndex()) {
                if (part.isEmpty() || part.length > 9) return null
                if (part.length > 1 && part[0] == '0') return null
                if (!part.all { it.isDigit() }) return null
                values[index] = part.toLongOrNull() ?: return null
            }
            return SemanticVersion(values[0], values[1], values[2])
        }
    }
}

data class ReleaseAsset(
    val id: Long = 0L,
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long = -1L,
    val contentType: String = ""
)

data class GitHubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val publishedAt: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset>
)

data class UpdateReleaseInfo(
    val release: GitHubRelease,
    val version: SemanticVersion,
    val apkAsset: ReleaseAsset,
    val checksumAsset: ReleaseAsset?
) {
    val tagName: String get() = release.tagName
    val htmlUrl: String get() = release.htmlUrl
}

enum class UpdateErrorKind {
    NETWORK,
    HTTP,
    RATE_LIMIT,
    NO_RELEASE,
    MISSING_APK,
    MALFORMED_METADATA,
    INVALID_VERSION,
    NOT_STABLE,
    DOWNLOAD_FAILED,
    STORAGE,
    CHECKSUM_MISMATCH,
    INVALID_APK,
    WRONG_PACKAGE,
    SIGNATURE_MISMATCH,
    NOT_NEWER,
    INSTALL_PERMISSION,
    INSTALLER_FAILED,
    UNKNOWN
}

class UpdateException(
    val kind: UpdateErrorKind,
    val httpStatus: Int? = null,
    message: String? = null,
    cause: Throwable? = null
) : Exception(message ?: kind.name, cause)

sealed interface UpdateCheckDecision {
    data object UpToDate : UpdateCheckDecision
    data class Available(val info: UpdateReleaseInfo) : UpdateCheckDecision
    data class Rejected(val kind: UpdateErrorKind) : UpdateCheckDecision
}

data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?
) {
    /** Null when the server did not provide a usable content length. */
    val fraction: Float? = totalBytes
        ?.takeIf { it > 0L }
        ?.let { total -> (downloadedBytes.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f) }

    val percent: Int? = fraction?.let { (it * 100f).toInt().coerceIn(0, 100) }
}

data class DownloadedUpdate(
    val tagName: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val checksumVerified: Boolean
)

enum class UpdatePhase {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    AVAILABLE,
    DOWNLOADING,
    VALIDATING,
    READY_TO_INSTALL,
    ERROR
}

data class UpdateUiState(
    val installedVersionName: String = "",
    val installedVersionCode: Long = 0L,
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val releaseInfo: UpdateReleaseInfo? = null,
    val promptVisible: Boolean = false,
    val progress: DownloadProgress? = null,
    val downloadedUpdate: DownloadedUpdate? = null,
    val errorKind: UpdateErrorKind? = null,
    val errorHttpStatus: Int? = null,
    val manualCheck: Boolean = false,
    val lastSuccessfulCheckEpochMillis: Long = 0L,
    val lastKnownTag: String = ""
)

object UpdatePolicy {
    const val REPOSITORY_URL: String = "https://github.com/kiriliodas/Unit-Converter-V2"
    const val LATEST_RELEASE_API_URL: String =
        "https://api.github.com/repos/kiriliodas/Unit-Converter-V2/releases/latest"
    const val RELEASE_PAGE_PREFIX: String = "$REPOSITORY_URL/releases/tag/"
    const val RELEASE_DOWNLOAD_PREFIX: String = "$REPOSITORY_URL/releases/download/"
    const val CHECK_INTERVAL_MILLIS: Long = 24L * 60L * 60L * 1000L

    fun shouldCheck(
        nowEpochMillis: Long,
        lastSuccessfulCheckEpochMillis: Long,
        manual: Boolean = false,
        intervalMillis: Long = CHECK_INTERVAL_MILLIS
    ): Boolean {
        if (manual) return true
        if (lastSuccessfulCheckEpochMillis <= 0L) return true
        val elapsed = nowEpochMillis - lastSuccessfulCheckEpochMillis
        return elapsed < 0L || elapsed >= intervalMillis
    }

    fun shouldPrompt(info: UpdateReleaseInfo, dismissedTag: String, manual: Boolean): Boolean =
        manual || !dismissedTag.equals(info.tagName, ignoreCase = true)

    fun decide(installedVersionName: String?, release: GitHubRelease): UpdateCheckDecision {
        if (release.draft || release.prerelease) {
            return UpdateCheckDecision.Rejected(UpdateErrorKind.NOT_STABLE)
        }
        val remote = SemanticVersion.parseReleaseTag(release.tagName)
            ?: return UpdateCheckDecision.Rejected(UpdateErrorKind.INVALID_VERSION)
        val installed = SemanticVersion.parseInstalledVersionName(installedVersionName)
            ?: return UpdateCheckDecision.Rejected(UpdateErrorKind.INVALID_VERSION)
        if (remote <= installed) return UpdateCheckDecision.UpToDate

        val apk = selectApkAsset(release.assets, remote)
            ?: return UpdateCheckDecision.Rejected(UpdateErrorKind.MISSING_APK)
        return UpdateCheckDecision.Available(
            UpdateReleaseInfo(
                release = release,
                version = remote,
                apkAsset = apk,
                checksumAsset = selectChecksumAsset(release.assets, apk)
            )
        )
    }

    /**
     * Selects one unambiguous APK. Official names such as
     * `Unit-Converter-V2.0.0.apk` rank highest; a lone `.apk` asset remains
     * acceptable so a harmless future rename does not require an app update.
     */
    fun selectApkAsset(assets: List<ReleaseAsset>, version: SemanticVersion): ReleaseAsset? {
        val candidates = assets.filter { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) &&
                isOfficialReleaseDownloadUrl(asset.downloadUrl) &&
                !asset.name.contains('/') && !asset.name.contains('\\')
        }
        if (candidates.isEmpty()) return null

        fun score(asset: ReleaseAsset): Int {
            val name = asset.name
            val lower = name.lowercase()
            return when {
                lower == "unit-converter-v$version.apk".lowercase() -> 100
                Regex("(?i)^unit-converter-v?\\d+\\.\\d+\\.\\d+\\.apk$").matches(name) -> 90
                lower.contains("unit-converter") -> 70
                lower.contains("unitconverter") -> 60
                else -> 10
            }
        }

        val ranked = candidates.sortedWith(
            compareByDescending<ReleaseAsset> { score(it) }.thenBy { it.name.lowercase() }
        )
        val best = ranked.first()
        val bestScore = score(best)
        return if (ranked.count { score(it) == bestScore } == 1) best else null
    }

    fun selectChecksumAsset(assets: List<ReleaseAsset>, apk: ReleaseAsset): ReleaseAsset? {
        val apkLower = apk.name.lowercase()
        val apkStem = apkLower.removeSuffix(".apk")
        val candidates = assets.filter { asset ->
            val lower = asset.name.lowercase()
            isOfficialReleaseDownloadUrl(asset.downloadUrl) &&
                (asset.sizeBytes < 0L || asset.sizeBytes <= MAX_CHECKSUM_BYTES) &&
                (
                    lower == "$apkLower.sha256" ||
                        lower == "$apkStem.sha256" ||
                        lower == "sha256sums" ||
                        lower == "sha256sums.txt" ||
                        lower == "checksums" ||
                        lower == "checksums.txt" ||
                        (lower.contains("sha256") && lower.contains(apkStem))
                    )
        }
        return candidates.firstOrNull { it.name.equals("${apk.name}.sha256", ignoreCase = true) }
            ?: candidates.singleOrNull()
    }

    fun isOfficialReleaseDownloadUrl(url: String): Boolean =
        url.startsWith(RELEASE_DOWNLOAD_PREFIX, ignoreCase = true)

    const val MAX_CHECKSUM_BYTES: Long = 1_048_576L
}

object Sha256 {
    private val HEX = Regex("(?i)^[0-9a-f]{64}$")

    fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { "%02x".format(it) }

    fun digest(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    fun isValid(value: String?): Boolean = value != null && HEX.matches(value.trim())

    fun verify(expectedHex: String?, actualHex: String?): Boolean =
        isValid(expectedHex) && isValid(actualHex) &&
            expectedHex!!.trim().equals(actualHex!!.trim(), ignoreCase = true)

    /**
     * Extracts a checksum for [fileName] from either a single-hash file or a
     * conventional `hash  filename` checksum list.
     */
    fun parseExpected(text: String, fileName: String): String? {
        val tokenRegex = Regex("(?i)(?<![0-9a-f])[0-9a-f]{64}(?![0-9a-f])")
        var first: String? = null
        for (line in text.lineSequence()) {
            val hash = tokenRegex.find(line)?.value?.lowercase() ?: continue
            if (first == null) first = hash
            val normalizedLine = line.replace('\\', '/').lowercase()
            if (normalizedLine.contains("/${fileName.lowercase()}") ||
                normalizedLine.trimEnd().endsWith(fileName.lowercase())
            ) {
                return hash
            }
        }
        // A checksum list with no matching filename is ambiguous; only use a
        // sole hash when it is the only candidate in the file.
        val all = tokenRegex.findAll(text).map { it.value.lowercase() }.distinct().toList()
        return if (all.size == 1) all.first() else first?.takeIf { all.size <= 1 }
    }
}

object ReleaseNotesFormatter {
    fun shortNotes(body: String, maxChars: Int = 900): String {
        if (body.isBlank()) return ""
        var text = body.replace("\r\n", "\n").replace('\r', '\n')
        text = text.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        text = text.replace(Regex("!\\[[^]]*]\\([^)]*\\)"), "")
        text = text.replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        text = text.replace(Regex("<[^>]+>"), "")
        val lines = text.lineSequence().map { raw ->
            raw.trim()
                .replace(Regex("^#{1,6}\\s+"), "")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
        }.filterNot { line ->
            line.startsWith("Full Changelog:", ignoreCase = true) ||
                line.startsWith("**Full Changelog**", ignoreCase = true)
        }.toList()

        val collapsed = buildString {
            var previousBlank = true
            for (line in lines) {
                if (line.isBlank()) {
                    if (!previousBlank) appendLine()
                    previousBlank = true
                } else {
                    appendLine(line)
                    previousBlank = false
                }
            }
        }.trim()

        if (collapsed.length <= maxChars) return collapsed
        val cut = collapsed.substring(0, maxChars)
        val boundary = maxOf(cut.lastIndexOf('\n'), cut.lastIndexOf(' '))
        return (if (boundary > maxChars / 2) cut.substring(0, boundary) else cut).trim() + "…"
    }
}
