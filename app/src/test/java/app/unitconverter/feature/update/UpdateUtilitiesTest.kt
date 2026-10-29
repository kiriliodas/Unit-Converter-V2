package app.unitconverter.feature.update

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateUtilitiesTest {

    @Test
    fun sha256DigestMatchesKnownValue() {
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertEquals(expected, Sha256.digest("abc".toByteArray()))
        assertEquals(expected, Sha256.digest(ByteArrayInputStream("abc".toByteArray())))
        assertTrue(Sha256.verify(expected.uppercase(), expected))
        assertFalse(Sha256.verify(expected, "0".repeat(64)))
    }

    @Test
    fun sha256ParserSelectsHashForApkFileName() {
        val wanted = "a".repeat(64)
        val other = "b".repeat(64)
        val list = """
            $other  other.apk
            $wanted  Unit-Converter-V2.1.0.apk
        """.trimIndent()
        assertEquals(wanted, Sha256.parseExpected(list, "Unit-Converter-V2.1.0.apk"))
        assertEquals(wanted, Sha256.parseExpected(wanted, "Unit-Converter-V2.1.0.apk"))
    }

    @Test
    fun sha256ParserRejectsAmbiguousChecksumList() {
        val list = "${"a".repeat(64)}  one.apk\n${"b".repeat(64)}  two.apk"
        assertNull(Sha256.parseExpected(list, "missing.apk"))
        assertNull(Sha256.parseExpected("not a checksum", "app.apk"))
    }

    @Test
    fun releaseNotesFormatterStripsMarkdownAndHtml() {
        val notes = ReleaseNotesFormatter.shortNotes(
            """
            ## Highlights
            - **Faster** conversion
            - See [details](https://example.invalid)
            <p>Plain text</p>
            """.trimIndent()
        )
        assertEquals("Highlights\n- Faster conversion\n- See details\nPlain text", notes)
    }

    @Test
    fun releaseNotesFormatterTruncatesLongBodies() {
        val notes = ReleaseNotesFormatter.shortNotes("word ".repeat(100), maxChars = 40)
        assertTrue(notes.length <= 41)
        assertTrue(notes.endsWith("…"))
    }

    @Test
    fun downloadProgressHandlesMissingAndKnownTotals() {
        assertNull(DownloadProgress(10L, null).percent)
        assertEquals(25, DownloadProgress(25L, 100L).percent)
        assertEquals(100, DownloadProgress(125L, 100L).percent)
    }
}
