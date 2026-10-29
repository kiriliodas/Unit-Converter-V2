package app.unitconverter.feature.support

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.unitconverter.R
import app.unitconverter.core.ui.AppIcons
import app.unitconverter.core.ui.theme.LocalExtendedColors
import app.unitconverter.feature.convert.SymbolIcon

object SupportContact {
    const val EMAIL: String = "mael.poignan@gmail.com"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportReportSheet(
    onDismiss: () -> Unit,
    onCompleted: () -> Unit = onDismiss
) {
    val context = LocalContext.current
    val extended = LocalExtendedColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val sheetStabilizer = remember {
        object : NestedScrollConnection {
            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                // The scrollable form has already consumed what it can. Drop
                // only leftover upward momentum while the sheet is settling
                // expanded, instead of letting it feed the sheet spring and
                // make the near-full-screen sheet bounce. Downward leftover
                // velocity still reaches the sheet, preserving swipe-to-close.
                return if (
                    available.y < 0f &&
                    sheetState.targetValue == SheetValue.Expanded
                ) {
                    available
                } else {
                    Velocity.Zero
                }
            }
        }
    }

    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var reproductionSteps by rememberSaveable { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<SupportAttachment>>(emptyList()) }
    var showValidationErrors by rememberSaveable { mutableStateOf(false) }
    var attachmentLimitReached by remember { mutableStateOf(false) }
    var showThanksDialog by remember { mutableStateOf(false) }

    val unknownFileName = stringResource(R.string.support_report_unknown_file)
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val incoming = uris.map { uri ->
                loadSupportAttachment(context, uri, unknownFileName)
            }
            val distinctCandidateCount = (attachments + incoming)
                .filter { it.uriString.isNotBlank() }
                .distinctBy { it.uriString }
                .size
            attachmentLimitReached =
                distinctCandidateCount > SupportReportFormatter.MAX_ATTACHMENTS
            attachments = SupportReportFormatter.mergeAttachments(attachments, incoming)
        }
    }

    // Mail apps report "canceled" whether the message was sent or not, so the
    // return of the mail activity is the only signal available. Any return
    // shows the thank-you dialog, whose confirmation closes the sheet.
    val emailLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        showThanksDialog = true
    }

    val deviceInfo = remember(context.applicationContext) {
        loadSupportDeviceInfo(context)
    }
    val labels = SupportReportLabels(
        description = stringResource(R.string.support_report_description_label),
        reproductionSteps = stringResource(R.string.support_report_steps_label),
        technicalInformation = stringResource(R.string.support_report_technical_info_title),
        appVersion = stringResource(R.string.support_report_app_version),
        androidVersion = stringResource(R.string.support_report_android_version),
        device = stringResource(R.string.support_report_device)
    )
    val appName = stringResource(R.string.app_name)
    val chooserTitle = stringResource(R.string.support_report_email_chooser_title)
    val noEmailAppMessage = stringResource(R.string.support_report_no_email_app)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        contentWindowInsets = {
            WindowInsets(left = 0.dp, top = 0.dp, right = 0.dp, bottom = 0.dp)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .nestedScroll(sheetStabilizer)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SymbolIcon(
                            iconRes = AppIcons.Flag,
                            contentDescription = null,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.support_report_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    SymbolIcon(
                        iconRes = AppIcons.Close,
                        contentDescription = stringResource(R.string.close),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.support_report_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SupportTextField(
                value = title,
                onValueChange = { title = it },
                label = stringResource(R.string.support_report_subject_label),
                placeholder = stringResource(R.string.support_report_subject_placeholder),
                isError = showValidationErrors && title.isBlank(),
                supportingText = if (showValidationErrors && title.isBlank()) {
                    stringResource(R.string.support_report_required)
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                )
            )

            SupportTextField(
                value = description,
                onValueChange = { description = it },
                label = stringResource(R.string.support_report_description_label),
                placeholder = stringResource(R.string.support_report_description_placeholder),
                modifier = Modifier.heightIn(min = 132.dp),
                isError = showValidationErrors && description.isBlank(),
                supportingText = if (showValidationErrors && description.isBlank()) {
                    stringResource(R.string.support_report_required)
                } else {
                    null
                },
                minLines = 5,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                )
            )

            SupportTextField(
                value = reproductionSteps,
                onValueChange = { reproductionSteps = it },
                label = stringResource(R.string.support_report_steps_label),
                placeholder = stringResource(R.string.support_report_steps_placeholder),
                modifier = Modifier.heightIn(min = 96.dp),
                minLines = 3,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                )
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.support_report_attachments_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${attachments.size}/${SupportReportFormatter.MAX_ATTACHMENTS}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = {
                        attachmentLimitReached = false
                        attachmentPicker.launch(arrayOf("*/*"))
                    },
                    enabled = attachments.size < SupportReportFormatter.MAX_ATTACHMENTS,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, extended.outlineStrong)
                ) {
                    SymbolIcon(
                        iconRes = AppIcons.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.support_report_add_attachment))
                }

                Text(
                    text = stringResource(
                        R.string.support_report_attachment_hint,
                        SupportReportFormatter.MAX_ATTACHMENTS
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (attachmentLimitReached) {
                    Text(
                        text = stringResource(
                            R.string.support_report_attachment_limit_reached,
                            SupportReportFormatter.MAX_ATTACHMENTS
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                attachments.forEach { attachment ->
                    SupportAttachmentRow(
                        attachment = attachment,
                        onRemove = {
                            attachments = attachments.filterNot {
                                it.uriString == attachment.uriString
                            }
                            attachmentLimitReached = false
                        }
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = extended.panel,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, extended.panelBorder)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.support_report_technical_info_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    TechnicalInfoRow(
                        label = stringResource(R.string.support_report_recipient_label),
                        value = SupportContact.EMAIL
                    )
                    TechnicalInfoRow(
                        label = labels.appVersion,
                        value = appVersionText(deviceInfo)
                    )
                    TechnicalInfoRow(
                        label = labels.androidVersion,
                        value = androidVersionText(deviceInfo)
                    )
                    TechnicalInfoRow(
                        label = labels.device,
                        value = deviceText(deviceInfo)
                    )
                    HorizontalDivider(color = extended.panelBorder)
                    Text(
                        text = stringResource(R.string.support_report_privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = {
                    val draft = SupportReportDraft(
                        title = title,
                        description = description,
                        reproductionSteps = reproductionSteps
                    )
                    if (!draft.isValid) {
                        showValidationErrors = true
                    } else {
                        val attachmentUris = attachments.map { it.uriString.toUri() }
                        val launched = launchSupportEmail(
                            context = context,
                            subject = SupportReportFormatter.subject(appName, draft.title),
                            body = SupportReportFormatter.body(draft, deviceInfo, labels),
                            attachmentUris = attachmentUris,
                            chooserTitle = chooserTitle,
                            launchIntent = { intent -> emailLauncher.launch(intent) }
                        )
                        if (!launched) {
                            Toast.makeText(context, noEmailAppMessage, Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = extended.ink,
                    contentColor = extended.onInk
                )
            ) {
                Text(text = stringResource(R.string.support_report_open_email))
            }
        }
    }

    if (showThanksDialog) {
        SupportReportThanksDialog(
            onConfirm = {
                showThanksDialog = false
                title = ""
                description = ""
                reproductionSteps = ""
                attachments = emptyList()
                showValidationErrors = false
                attachmentLimitReached = false
                onCompleted()
            }
        )
    }
}

@Composable
private fun SupportReportThanksDialog(onConfirm: () -> Unit) {
    val extended = LocalExtendedColors.current
    Dialog(onDismissRequest = onConfirm) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SymbolIcon(
                            iconRes = AppIcons.Check,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.support_report_thanks_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )
                Text(
                    text = stringResource(R.string.support_report_thanks_message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                        .heightIn(min = 52.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = extended.ink,
                        contentColor = extended.onInk
                    )
                ) {
                    Text(text = stringResource(R.string.support_report_thanks_button))
                }
            }
        }
    }
}

@Composable
private fun SupportTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val extended = LocalExtendedColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = label) },
        placeholder = {
            Text(
                text = placeholder,
                maxLines = if (singleLine) 1 else 3,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingText = supportingText?.let { message ->
            { Text(text = message) }
        },
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            disabledTextColor = extended.disabledContent,
            focusedContainerColor = extended.panel,
            unfocusedContainerColor = extended.panel,
            disabledContainerColor = extended.panel,
            errorContainerColor = extended.panel,
            cursorColor = MaterialTheme.colorScheme.primary,
            errorCursorColor = MaterialTheme.colorScheme.error,
            focusedBorderColor = extended.ink,
            unfocusedBorderColor = extended.panelBorder,
            disabledBorderColor = extended.panelBorder,
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedLabelColor = MaterialTheme.colorScheme.onSurface,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            errorLabelColor = MaterialTheme.colorScheme.error,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            errorSupportingTextColor = MaterialTheme.colorScheme.error
        )
    )
}

@Composable
private fun SupportAttachmentRow(
    attachment: SupportAttachment,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val extended = LocalExtendedColors.current
    val extension = attachment.displayName
        .substringAfterLast('.', missingDelimiterValue = "")
        .uppercase()
        .take(4)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, extended.panelBorder)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = extended.panel,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                border = BorderStroke(1.dp, extended.panelBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = extension.ifBlank { "•" },
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val size = attachment.sizeBytes
                if (size != null && size >= 0L) {
                    Text(
                        text = Formatter.formatShortFileSize(context, size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(48.dp)
            ) {
                SymbolIcon(
                    iconRes = AppIcons.Close,
                    contentDescription = stringResource(
                        R.string.support_report_remove_attachment,
                        attachment.displayName
                    ),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun TechnicalInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.58f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun appVersionText(info: SupportDeviceInfo): String = buildString {
    if (info.appVersionName.isNotBlank()) append(info.appVersionName.trim())
    if (info.appVersionCode > 0L) {
        if (isNotEmpty()) append(' ')
        append('(')
        append(info.appVersionCode)
        append(')')
    }
}

private fun androidVersionText(info: SupportDeviceInfo): String = buildString {
    if (info.androidRelease.isNotBlank()) append(info.androidRelease.trim())
    if (info.androidSdkInt > 0) {
        if (isNotEmpty()) append(' ')
        append("(API ")
        append(info.androidSdkInt)
        append(')')
    }
}

private fun deviceText(info: SupportDeviceInfo): String =
    listOf(info.manufacturer, info.model)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .joinToString(separator = " ")

private fun loadSupportDeviceInfo(context: Context): SupportDeviceInfo {
    val appContext = context.applicationContext
    val packageInfo = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0)
    }.getOrNull()
    val versionCode = packageInfo?.let { info ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    } ?: 0L

    return SupportDeviceInfo(
        appVersionName = packageInfo?.versionName.orEmpty(),
        appVersionCode = versionCode,
        androidRelease = Build.VERSION.RELEASE.orEmpty(),
        androidSdkInt = Build.VERSION.SDK_INT,
        manufacturer = Build.MANUFACTURER.orEmpty(),
        model = Build.MODEL.orEmpty()
    )
}

private fun loadSupportAttachment(
    context: Context,
    uri: Uri,
    fallbackName: String
): SupportAttachment {
    var displayName: String? = null
    var sizeBytes: Long? = null

    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex)
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex).takeIf { it >= 0L }
                }
            }
        }
    }

    val fallbackFromUri = uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
    return SupportAttachment(
        uriString = uri.toString(),
        displayName = displayName?.takeIf { it.isNotBlank() } ?: fallbackFromUri ?: fallbackName,
        sizeBytes = sizeBytes
    )
}

private fun launchSupportEmail(
    context: Context,
    subject: String,
    body: String,
    attachmentUris: List<Uri>,
    chooserTitle: String,
    launchIntent: (Intent) -> Unit
): Boolean {
    if (attachmentUris.isEmpty()) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:${SupportContact.EMAIL}".toUri()
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SupportContact.EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return launchSupportChooser(
            target = intent,
            initialTargets = emptyList(),
            chooserTitle = chooserTitle,
            clipData = null,
            launchIntent = launchIntent
        )
    }

    val mailtoIntent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:".toUri()
    }
    val mailPackages = querySupportActivities(context, mailtoIntent)
        .mapNotNull { it.activityInfo?.packageName }
        .distinct()

    fun targetedMailIntents(mimeType: String): List<Intent> =
        mailPackages.map { packageName ->
            createAttachmentEmailIntent(
                context = context,
                subject = subject,
                body = body,
                attachmentUris = attachmentUris,
                mimeType = mimeType
            ).apply {
                setPackage(packageName)
            }
        }.filter { intent ->
            querySupportActivities(context, intent).isNotEmpty()
        }

    val targetedRfcIntents = targetedMailIntents("message/rfc822")
    val firstTargetedRfc = targetedRfcIntents.firstOrNull()
    if (firstTargetedRfc != null) {
        return launchSupportChooser(
            target = firstTargetedRfc,
            initialTargets = targetedRfcIntents.drop(1),
            chooserTitle = chooserTitle,
            clipData = firstTargetedRfc.clipData,
            launchIntent = launchIntent
        )
    }

    val rfcIntent = createAttachmentEmailIntent(
        context = context,
        subject = subject,
        body = body,
        attachmentUris = attachmentUris,
        mimeType = "message/rfc822"
    )
    if (querySupportActivities(context, rfcIntent).isNotEmpty()) {
        return launchSupportChooser(
            target = rfcIntent,
            initialTargets = emptyList(),
            chooserTitle = chooserTitle,
            clipData = rfcIntent.clipData,
            launchIntent = launchIntent
        )
    }

    val targetedGenericIntents = targetedMailIntents("*/*")
    val firstTargetedGeneric = targetedGenericIntents.firstOrNull() ?: return false
    return launchSupportChooser(
        target = firstTargetedGeneric,
        initialTargets = targetedGenericIntents.drop(1),
        chooserTitle = chooserTitle,
        clipData = firstTargetedGeneric.clipData,
        launchIntent = launchIntent
    )
}

private fun launchSupportChooser(
    target: Intent,
    initialTargets: List<Intent>,
    chooserTitle: String,
    clipData: ClipData?,
    launchIntent: (Intent) -> Unit
): Boolean {
    return try {
        val chooser = Intent.createChooser(target, chooserTitle).apply {
            if (initialTargets.isNotEmpty()) {
                putExtra(
                    Intent.EXTRA_INITIAL_INTENTS,
                    initialTargets.toTypedArray()
                )
            }
            if (clipData != null) {
                this.clipData = clipData
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        launchIntent(chooser)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

@Suppress("DEPRECATION")
private fun querySupportActivities(
    context: Context,
    intent: Intent
): List<ResolveInfo> = runCatching {
    context.packageManager.queryIntentActivities(
        intent,
        PackageManager.MATCH_DEFAULT_ONLY
    )
}.getOrDefault(emptyList())

private fun createAttachmentEmailIntent(
    context: Context,
    subject: String,
    body: String,
    attachmentUris: List<Uri>,
    mimeType: String
): Intent {
    val action = if (attachmentUris.size == 1) {
        Intent.ACTION_SEND
    } else {
        Intent.ACTION_SEND_MULTIPLE
    }
    return Intent(action).apply {
        type = mimeType
        putExtra(Intent.EXTRA_EMAIL, arrayOf(SupportContact.EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        if (attachmentUris.size == 1) {
            putExtra(Intent.EXTRA_STREAM, attachmentUris.first())
        } else {
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                ArrayList(attachmentUris)
            )
        }
        clipData = ClipData.newUri(
            context.contentResolver,
            "attachments",
            attachmentUris.first()
        ).apply {
            attachmentUris.drop(1).forEach { uri ->
                addItem(ClipData.Item(uri))
            }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
