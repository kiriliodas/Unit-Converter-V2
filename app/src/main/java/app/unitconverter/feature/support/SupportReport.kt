package app.unitconverter.feature.support

/** User-entered problem-report content. Attachments stay outside the body. */
data class SupportReportDraft(
    val title: String = "",
    val description: String = "",
    val reproductionSteps: String = ""
) {
    val isValid: Boolean
        get() = title.isNotBlank() && description.isNotBlank()
}

/** Minimal technical context shown to, and editable by, the sender in email. */
data class SupportDeviceInfo(
    val appVersionName: String,
    val appVersionCode: Long,
    val androidRelease: String,
    val androidSdkInt: Int,
    val manufacturer: String,
    val model: String
)

/** A user-selected attachment reference; the file itself is never copied. */
data class SupportAttachment(
    val uriString: String,
    val displayName: String,
    val sizeBytes: Long?
)

/** Localized e-mail section labels supplied by the Compose layer. */
data class SupportReportLabels(
    val description: String,
    val reproductionSteps: String,
    val technicalInformation: String,
    val appVersion: String,
    val androidVersion: String,
    val device: String
)

object SupportReportFormatter {
    const val MAX_ATTACHMENTS: Int = 5
    private const val MAX_SUBJECT_TITLE_CHARS: Int = 120

    fun subject(appName: String, title: String): String {
        val normalizedTitle = title
            .replace(Regex("[\\p{Cntrl}&&[^\\t]]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_SUBJECT_TITLE_CHARS)
            .trim()
        return if (normalizedTitle.isBlank()) {
            "[$appName]"
        } else {
            "[$appName] $normalizedTitle"
        }
    }

    fun body(
        draft: SupportReportDraft,
        deviceInfo: SupportDeviceInfo,
        labels: SupportReportLabels
    ): String = buildString {
        appendLine(labels.description)
        appendLine(draft.description.trim())

        val steps = draft.reproductionSteps.trim()
        if (steps.isNotEmpty()) {
            appendLine()
            appendLine(labels.reproductionSteps)
            appendLine(steps)
        }

        appendLine()
        appendLine(labels.technicalInformation)

        val appVersion = buildString {
            if (deviceInfo.appVersionName.isNotBlank()) {
                append(deviceInfo.appVersionName.trim())
            }
            if (deviceInfo.appVersionCode > 0L) {
                if (isNotEmpty()) append(' ')
                append('(')
                append(deviceInfo.appVersionCode)
                append(')')
            }
        }
        if (appVersion.isNotBlank()) {
            appendLine("${labels.appVersion}: $appVersion")
        }

        val androidVersion = buildString {
            if (deviceInfo.androidRelease.isNotBlank()) {
                append(deviceInfo.androidRelease.trim())
            }
            if (deviceInfo.androidSdkInt > 0) {
                if (isNotEmpty()) append(' ')
                append("(API ")
                append(deviceInfo.androidSdkInt)
                append(')')
            }
        }
        if (androidVersion.isNotBlank()) {
            appendLine("${labels.androidVersion}: $androidVersion")
        }

        val device = listOf(deviceInfo.manufacturer, deviceInfo.model)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .joinToString(separator = " ")
        if (device.isNotBlank()) {
            appendLine("${labels.device}: $device")
        }
    }.trimEnd()

    fun mergeAttachments(
        existing: List<SupportAttachment>,
        incoming: List<SupportAttachment>
    ): List<SupportAttachment> = (existing + incoming)
        .filter { it.uriString.isNotBlank() }
        .distinctBy { it.uriString }
        .take(MAX_ATTACHMENTS)
}
