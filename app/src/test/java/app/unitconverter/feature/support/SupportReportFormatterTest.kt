package app.unitconverter.feature.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportReportFormatterTest {
    private val labels = SupportReportLabels(
        description = "Description",
        reproductionSteps = "Steps to reproduce",
        technicalInformation = "Technical information",
        appVersion = "App version",
        androidVersion = "Android version",
        device = "Device"
    )
    private val device = SupportDeviceInfo(
        appVersionName = "2.1.2",
        appVersionCode = 4L,
        androidRelease = "15",
        androidSdkInt = 35,
        manufacturer = "Google",
        model = "Pixel 8"
    )

    @Test
    fun draftRequiresTitleAndDescription() {
        assertFalse(SupportReportDraft().isValid)
        assertFalse(SupportReportDraft(title = "Crash").isValid)
        assertFalse(SupportReportDraft(description = "It closes").isValid)
        assertTrue(
            SupportReportDraft(
                title = "Crash",
                description = "The app closes after tapping Convert."
            ).isValid
        )
    }

    @Test
    fun subjectPrefixesAppNameAndCollapsesWhitespace() {
        assertEquals(
            "[Unit Converter] Wrong result for miles",
            SupportReportFormatter.subject(
                appName = "Unit Converter",
                title = "  Wrong result\nfor\tmiles  "
            )
        )
        assertEquals(
            "[Unit Converter]",
            SupportReportFormatter.subject("Unit Converter", " \n ")
        )
    }

    @Test
    fun bodyIncludesOptionalStepsAndMinimalDeviceInfo() {
        val body = SupportReportFormatter.body(
            draft = SupportReportDraft(
                title = "Wrong result",
                description = "The displayed result is unexpected.",
                reproductionSteps = "1. Open Convert\n2. Enter 1"
            ),
            deviceInfo = device,
            labels = labels
        )

        assertTrue(body.contains("Description"))
        assertTrue(body.contains("The displayed result is unexpected."))
        assertTrue(body.contains("Steps to reproduce"))
        assertTrue(body.contains("App version: 2.1.2 (4)"))
        assertTrue(body.contains("Android version: 15 (API 35)"))
        assertTrue(body.contains("Device: Google Pixel 8"))
    }

    @Test
    fun bodyOmitsBlankReproductionSteps() {
        val body = SupportReportFormatter.body(
            draft = SupportReportDraft(
                title = "Display issue",
                description = "A label is cut off.",
                reproductionSteps = "  "
            ),
            deviceInfo = device,
            labels = labels
        )

        assertFalse(body.contains("Steps to reproduce"))
        assertTrue(body.contains("Technical information"))
    }

    @Test
    fun attachmentsAreDeduplicatedAndCapped() {
        fun attachment(index: Int) = SupportAttachment(
            uriString = "content://files/$index",
            displayName = "file-$index.png",
            sizeBytes = 100L
        )

        val merged = SupportReportFormatter.mergeAttachments(
            existing = listOf(attachment(1), attachment(2)),
            incoming = listOf(
                attachment(2),
                attachment(3),
                attachment(4),
                attachment(5),
                attachment(6)
            )
        )

        assertEquals(
            listOf(
                "content://files/1",
                "content://files/2",
                "content://files/3",
                "content://files/4",
                "content://files/5"
            ),
            merged.map { it.uriString }
        )
    }
}
