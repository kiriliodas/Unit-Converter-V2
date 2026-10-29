package app.unitconverter.feature.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.security.MessageDigest

data class ValidatedApk(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val signerSha256: Set<String>
)

/**
 * Android-side APK checks performed before the system installer is opened.
 * The final installation decision still belongs to Android; this class never
 * attempts to bypass package/signature enforcement.
 */
class ApkValidator(context: Context) {

    private val appContext: Context = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager

    fun installedVersionName(): String? = installedPackageInfo(signing = false)?.versionName

    fun installedVersionCode(): Long = installedPackageInfo(signing = false)?.let(::longVersionCode) ?: 0L

    fun validate(file: File, expectedVersion: SemanticVersion): ValidatedApk {
        if (!file.isFile || file.length() <= 0L) {
            throw UpdateException(UpdateErrorKind.INVALID_APK)
        }

        val archive = archivePackageInfo(file.absolutePath, signing = true)
            ?: throw UpdateException(UpdateErrorKind.INVALID_APK)
        val installed = installedPackageInfo(signing = true)
            ?: throw UpdateException(UpdateErrorKind.UNKNOWN)

        val downloadedPackage = archive.packageName.orEmpty()
        if (downloadedPackage != appContext.packageName) {
            throw UpdateException(UpdateErrorKind.WRONG_PACKAGE)
        }

        val downloadedCode = longVersionCode(archive)
        val installedCode = longVersionCode(installed)
        if (downloadedCode <= installedCode) {
            throw UpdateException(UpdateErrorKind.NOT_NEWER)
        }

        val downloadedVersionName = archive.versionName.orEmpty()
        val parsedDownloaded = SemanticVersion.parseInstalledVersionName(downloadedVersionName)
            ?: throw UpdateException(UpdateErrorKind.INVALID_VERSION)
        if (parsedDownloaded != expectedVersion) {
            throw UpdateException(UpdateErrorKind.INVALID_VERSION)
        }

        val downloadedSigners = signerDigests(archive) {
            archivePackageInfo(file.absolutePath, signing = true, legacySignatures = true)
        }
        val installedSigners = signerDigests(installed) {
            installedPackageInfo(signing = true, legacySignatures = true)
        }
        if (downloadedSigners.isEmpty() || installedSigners.isEmpty()) {
            throw UpdateException(UpdateErrorKind.INVALID_APK)
        }
        if (downloadedSigners != installedSigners) {
            throw UpdateException(UpdateErrorKind.SIGNATURE_MISMATCH)
        }

        return ValidatedApk(
            packageName = downloadedPackage,
            versionName = downloadedVersionName,
            versionCode = downloadedCode,
            signerSha256 = downloadedSigners
        )
    }

    private fun archivePackageInfo(
        path: String,
        signing: Boolean,
        legacySignatures: Boolean = false
    ): PackageInfo? {
        val flags = signingFlags(signing, legacySignatures)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            archivePackageInfo33(path, flags)
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(path, flags)
        }
    }

    private fun installedPackageInfo(
        signing: Boolean,
        legacySignatures: Boolean = false
    ): PackageInfo? = runCatching {
        val flags = signingFlags(signing, legacySignatures)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            installedPackageInfo33(flags)
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(appContext.packageName, flags)
        }
    }.getOrNull()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun archivePackageInfo33(path: String, flags: Int): PackageInfo? =
        packageManager.getPackageArchiveInfo(
            path,
            PackageManager.PackageInfoFlags.of(flags.toLong())
        )

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun installedPackageInfo33(flags: Int): PackageInfo =
        packageManager.getPackageInfo(
            appContext.packageName,
            PackageManager.PackageInfoFlags.of(flags.toLong())
        )

    private fun signingFlags(signing: Boolean, legacySignatures: Boolean): Int {
        if (!signing) return 0
        return if (legacySignatures || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        } else {
            PackageManager.GET_SIGNING_CERTIFICATES
        }
    }

    private fun signerDigests(
        info: PackageInfo,
        legacyInfoProvider: () -> PackageInfo?
    ): Set<String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val modern = signerDigests28(info)
            if (modern.isNotEmpty()) return modern
        }
        val direct = legacySignerDigests(info)
        if (direct.isNotEmpty()) return direct
        val legacy = legacyInfoProvider() ?: return emptySet()
        return legacySignerDigests(legacy)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun signerDigests28(info: PackageInfo): Set<String> {
        val signingInfo = info.signingInfo ?: return emptySet()
        val signatures: Array<Signature> = if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        }
        return signatures.orEmptySignatures().mapTo(linkedSetOf()) { signature ->
            sha256Hex(signature.toByteArray())
        }
    }

    @Suppress("DEPRECATION")
    private fun legacySignerDigests(info: PackageInfo): Set<String> =
        info.signatures.orEmptySignatures().mapTo(linkedSetOf()) { signature ->
            sha256Hex(signature.toByteArray())
        }

    private fun Array<Signature>?.orEmptySignatures(): List<Signature> =
        this?.filterNotNull().orEmpty()

    private fun longVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { "%02x".format(it) }
}
