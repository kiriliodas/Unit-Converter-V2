package app.unitconverter.feature.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

sealed interface InstallLaunch {
    data class Ready(val intent: Intent) : InstallLaunch
    data class NeedsInstallPermission(val intent: Intent) : InstallLaunch
    data class Failed(val kind: UpdateErrorKind) : InstallLaunch
}

/**
 * Creates the standard Android package-installer handoff. Installation is
 * never silent: Android shows its own confirmation UI and enforces package
 * identity, downgrade rules, and signing-certificate compatibility.
 */
class UpdateInstaller(context: Context) {

    private val appContext: Context = context.applicationContext

    fun createInstallLaunch(file: File): InstallLaunch {
        if (!file.isFile || file.length() <= 0L) {
            return InstallLaunch.Failed(UpdateErrorKind.INVALID_APK)
        }

        // minSdk is 26, where per-app "install unknown apps" applies. The
        // manifest declares REQUEST_INSTALL_PACKAGES so this setting exists.
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${appContext.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return InstallLaunch.NeedsInstallPermission(permissionIntent)
        }

        val uri = try {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            )
        } catch (error: IllegalArgumentException) {
            return InstallLaunch.Failed(UpdateErrorKind.INVALID_APK)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolved = appContext.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return if (resolved == null) {
            InstallLaunch.Failed(UpdateErrorKind.INSTALLER_FAILED)
        } else {
            InstallLaunch.Ready(intent)
        }
    }

    companion object {
        const val APK_MIME_TYPE: String = "application/vnd.android.package-archive"
    }
}
