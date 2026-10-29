package app.unitconverter.feature.update

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.unitconverter.R
import app.unitconverter.core.ui.AppIcons
import app.unitconverter.core.ui.theme.LocalExtendedColors
import app.unitconverter.feature.convert.SymbolIcon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Creates the update manager once per Activity composition and hosts its dialog above the app shell. */
@Composable
fun UpdateHost(
    content: @Composable (UpdateManager) -> Unit
) {
    val context = LocalContext.current
    val manager = remember(context.applicationContext) {
        UpdateManager(context.applicationContext)
    }

    LaunchedEffect(manager) {
        manager.checkOnStartup()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content(manager)
        UpdateDialog(manager)
    }
}

@Composable
private fun UpdateDialog(manager: UpdateManager) {
    val state by manager.state.collectAsState()
    if (!state.promptVisible) return

    val phase = state.phase
    val shouldShow = phase == UpdatePhase.AVAILABLE ||
        phase == UpdatePhase.DOWNLOADING ||
        phase == UpdatePhase.VALIDATING ||
        phase == UpdatePhase.READY_TO_INSTALL ||
        phase == UpdatePhase.ERROR
    if (!shouldShow) return

    val extended = LocalExtendedColors.current
    val context = LocalContext.current
    var releaseNotesOpenFailed by remember(state.releaseInfo?.tagName) { mutableStateOf(false) }
    val releaseNotes = remember(state.releaseInfo?.release?.body.orEmpty()) {
        ReleaseNotesFormatter.shortNotes(state.releaseInfo?.release?.body.orEmpty())
    }

    Dialog(
        onDismissRequest = { manager.dismissPrompt() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(28.dp),
            color = extended.panel,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, extended.panelBorder),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = extended.ink,
                        contentColor = extended.onInk
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            SymbolIcon(
                                iconRes = when (phase) {
                                    UpdatePhase.READY_TO_INSTALL -> AppIcons.Check
                                    UpdatePhase.ERROR -> AppIcons.Error
                                    else -> AppIcons.Convert.filled
                                },
                                contentDescription = null,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                when (phase) {
                                    UpdatePhase.DOWNLOADING -> R.string.update_downloading_title
                                    UpdatePhase.VALIDATING -> R.string.update_validating_title
                                    UpdatePhase.READY_TO_INSTALL -> R.string.update_ready_title
                                    UpdatePhase.ERROR -> R.string.update_error_title
                                    else -> R.string.update_available_title
                                }
                            ),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        state.releaseInfo?.let { info ->
                            Text(
                                text = info.release.name.ifBlank { info.tagName },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = { manager.dismissPrompt() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        SymbolIcon(
                            iconRes = AppIcons.Close,
                            contentDescription = stringResource(R.string.close),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                state.releaseInfo?.let { info ->
                    VersionRows(state, info)
                    ReleaseMeta(info)
                    if (releaseNotes.isNotBlank() && phase != UpdatePhase.ERROR) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.update_release_notes_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = releaseNotes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                when (phase) {
                    UpdatePhase.DOWNLOADING -> DownloadProgressContent(state)
                    UpdatePhase.VALIDATING -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.update_validating_summary),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    UpdatePhase.READY_TO_INSTALL -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.update_ready_summary),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(
                                    if (state.downloadedUpdate?.checksumVerified == true) {
                                        R.string.update_checksum_verified
                                    } else {
                                        R.string.update_checksum_unavailable
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.update_install_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> Unit
                }

                state.errorKind?.let { kind ->
                    if (phase == UpdatePhase.ERROR || kind == UpdateErrorKind.INSTALL_PERMISSION) {
                        Text(
                            text = updateErrorText(kind, state.errorHttpStatus),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (releaseNotesOpenFailed) {
                    Text(
                        text = updateErrorText(UpdateErrorKind.INSTALLER_FAILED, null),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (phase) {
                        UpdatePhase.AVAILABLE -> {
                            PrimaryUpdateButton(
                                text = stringResource(R.string.update_download_action),
                                onClick = { manager.downloadUpdate() }
                            )
                            OutlinedUpdateButton(
                                text = stringResource(R.string.update_view_release_notes_action),
                                onClick = {
                                    val url = state.releaseInfo?.htmlUrl
                                    releaseNotesOpenFailed = if (url.isNullOrBlank()) {
                                        true
                                    } else {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            )
                                        }.isFailure
                                    }
                                }
                            )
                        }

                        UpdatePhase.DOWNLOADING -> {
                            OutlinedUpdateButton(
                                text = stringResource(R.string.update_cancel_download_action),
                                onClick = { manager.cancelDownload() }
                            )
                        }

                        UpdatePhase.VALIDATING -> {
                            PrimaryUpdateButton(
                                text = stringResource(R.string.update_validating_title),
                                onClick = {},
                                enabled = false
                            )
                        }

                        UpdatePhase.READY_TO_INSTALL -> {
                            PrimaryUpdateButton(
                                text = stringResource(R.string.update_install_action),
                                onClick = { manager.installDownloadedUpdate() }
                            )
                            OutlinedUpdateButton(
                                text = stringResource(R.string.update_delete_download_action),
                                onClick = { manager.deleteDownloadedUpdate() }
                            )
                        }

                        UpdatePhase.ERROR -> {
                            PrimaryUpdateButton(
                                text = stringResource(R.string.update_retry_action),
                                onClick = { manager.retryLastAction() }
                            )
                        }

                        else -> Unit
                    }

                    TextButton(
                        onClick = { manager.dismissPrompt() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.update_later_action))
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateSettingsCard(manager: UpdateManager) {
    val state by manager.state.collectAsState()
    val extended = LocalExtendedColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = extended.panel,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, extended.panelBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SymbolIcon(
                            iconRes = AppIcons.Convert.outlined,
                            contentDescription = null,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.update_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = settingsSummary(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.phase == UpdatePhase.CHECKING || state.phase == UpdatePhase.VALIDATING) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }

            if (state.phase == UpdatePhase.DOWNLOADING) {
                DownloadProgressContent(state)
            }

            when (state.phase) {
                UpdatePhase.AVAILABLE -> {
                    PrimaryUpdateButton(
                        text = stringResource(R.string.update_view_action),
                        onClick = { manager.showPrompt() }
                    )
                }

                UpdatePhase.DOWNLOADING -> {
                    OutlinedUpdateButton(
                        text = stringResource(R.string.update_cancel_download_action),
                        onClick = { manager.cancelDownload() }
                    )
                }

                UpdatePhase.READY_TO_INSTALL -> {
                    PrimaryUpdateButton(
                        text = stringResource(R.string.update_install_action),
                        onClick = { manager.installDownloadedUpdate() }
                    )
                    TextButton(
                        onClick = { manager.deleteDownloadedUpdate() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.update_delete_download_action))
                    }
                }

                UpdatePhase.ERROR -> {
                    PrimaryUpdateButton(
                        text = stringResource(R.string.update_retry_action),
                        onClick = { manager.retryLastAction() }
                    )
                }

                UpdatePhase.CHECKING, UpdatePhase.VALIDATING -> {
                    PrimaryUpdateButton(
                        text = stringResource(R.string.update_check_action),
                        onClick = {},
                        enabled = false
                    )
                }

                else -> {
                    PrimaryUpdateButton(
                        text = stringResource(R.string.update_check_action),
                        onClick = { manager.checkManually() }
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionRows(state: UpdateUiState, info: UpdateReleaseInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VersionRow(stringResource(R.string.update_current_label), state.installedVersionName)
            VersionRow(stringResource(R.string.update_available_label), info.version.toString())
        }
    }
}

@Composable
private fun VersionRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReleaseMeta(info: UpdateReleaseInfo) {
    val context = LocalContext.current
    val published = remember(info.release.publishedAt) { formatPublishedAt(info.release.publishedAt) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (published.isNotBlank()) {
            Text(
                text = stringResource(R.string.update_published_format, published),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (info.apkAsset.sizeBytes > 0L) {
            Text(
                text = stringResource(
                    R.string.update_download_size_format,
                    Formatter.formatFileSize(context, info.apkAsset.sizeBytes)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DownloadProgressContent(state: UpdateUiState) {
    val context = LocalContext.current
    val progress = state.progress
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val fraction = progress?.fraction
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        val label = when {
            progress?.percent != null && progress.totalBytes != null -> stringResource(
                R.string.update_progress_bytes_format,
                Formatter.formatFileSize(context, progress.downloadedBytes),
                Formatter.formatFileSize(context, progress.totalBytes)
            ) + " · " + stringResource(R.string.update_progress_percent_format, progress.percent)

            progress != null -> stringResource(
                R.string.update_progress_downloaded_format,
                Formatter.formatFileSize(context, progress.downloadedBytes)
            )

            else -> stringResource(R.string.update_downloading_summary)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PrimaryUpdateButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val extended = LocalExtendedColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = extended.ink,
            contentColor = extended.onInk,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContentColor = extended.disabledContent
        )
    ) {
        Text(text)
    }
}

@Composable
private fun OutlinedUpdateButton(
    text: String,
    onClick: () -> Unit
) {
    val extended = LocalExtendedColors.current
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, extended.outlineStrong),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(text)
    }
}

@Composable
private fun settingsSummary(state: UpdateUiState): String = when (state.phase) {
    UpdatePhase.CHECKING -> stringResource(R.string.update_checking)
    UpdatePhase.UP_TO_DATE -> stringResource(
        R.string.update_up_to_date_summary,
        state.installedVersionName
    )
    UpdatePhase.AVAILABLE -> stringResource(
        R.string.update_available_summary_format,
        state.releaseInfo?.version?.toString().orEmpty()
    )
    UpdatePhase.DOWNLOADING -> stringResource(R.string.update_downloading_summary)
    UpdatePhase.VALIDATING -> stringResource(R.string.update_validating_summary)
    UpdatePhase.READY_TO_INSTALL -> stringResource(R.string.update_ready_summary)
    UpdatePhase.ERROR -> state.errorKind?.let { updateErrorText(it, state.errorHttpStatus) }
        ?: stringResource(R.string.update_error_unknown)
    UpdatePhase.IDLE -> state.errorKind?.let { updateErrorText(it, state.errorHttpStatus) }
        ?: stringResource(R.string.update_idle_summary)
}

@Composable
private fun updateErrorText(kind: UpdateErrorKind, httpStatus: Int?): String = when (kind) {
    UpdateErrorKind.NETWORK -> stringResource(R.string.update_error_network)
    UpdateErrorKind.HTTP -> stringResource(R.string.update_error_http_format, httpStatus ?: 0)
    UpdateErrorKind.RATE_LIMIT -> stringResource(R.string.update_error_rate_limit)
    UpdateErrorKind.NO_RELEASE -> stringResource(R.string.update_error_no_release)
    UpdateErrorKind.MISSING_APK -> stringResource(R.string.update_error_missing_apk)
    UpdateErrorKind.MALFORMED_METADATA -> stringResource(R.string.update_error_malformed)
    UpdateErrorKind.INVALID_VERSION -> stringResource(R.string.update_error_invalid_version)
    UpdateErrorKind.NOT_STABLE -> stringResource(R.string.update_error_not_stable)
    UpdateErrorKind.DOWNLOAD_FAILED -> stringResource(R.string.update_error_download_failed)
    UpdateErrorKind.STORAGE -> stringResource(R.string.update_error_storage)
    UpdateErrorKind.CHECKSUM_MISMATCH -> stringResource(R.string.update_error_checksum)
    UpdateErrorKind.INVALID_APK -> stringResource(R.string.update_error_invalid_apk)
    UpdateErrorKind.WRONG_PACKAGE -> stringResource(R.string.update_error_wrong_package)
    UpdateErrorKind.SIGNATURE_MISMATCH -> stringResource(R.string.update_error_signature)
    UpdateErrorKind.NOT_NEWER -> stringResource(R.string.update_error_not_newer)
    UpdateErrorKind.INSTALL_PERMISSION -> stringResource(R.string.update_error_install_permission)
    UpdateErrorKind.INSTALLER_FAILED -> stringResource(R.string.update_error_installer_failed)
    UpdateErrorKind.UNKNOWN -> stringResource(R.string.update_error_unknown)
}

private fun formatPublishedAt(raw: String): String {
    if (raw.isBlank()) return ""
    return runCatching {
        val date = Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDate()
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .format(date)
    }.getOrElse { raw.substringBefore('T') }
}
