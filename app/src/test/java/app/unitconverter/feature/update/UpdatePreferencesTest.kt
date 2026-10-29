package app.unitconverter.feature.update

import app.unitconverter.feature.convert.StringStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePreferencesTest {

    @Test
    fun serializerRoundTripsSeparateUpdateState() {
        val original = UpdatePersistedState(
            lastSuccessfulCheckEpochMillis = 1_729_000_000_000L,
            dismissedTag = "v2.0.1",
            lastKnownTag = "v2.1.0",
            downloadedUpdate = DownloadedUpdate(
                tagName = "v2.1.0",
                fileName = "unit-converter-v2.1.0-12.apk",
                sizeBytes = 8_386_382L,
                sha256 = "a".repeat(64),
                checksumVerified = true
            )
        )

        val decoded = UpdateStateSerializer.decode(UpdateStateSerializer.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun serializerIgnoresUnknownAndMalformedRecords() {
        val decoded = UpdateStateSerializer.decode(
            """
            UC_UPDATE_STATE_V1
            UNKNOWN${'\t'}value
            LAST_SUCCESSFUL_CHECK${'\t'}not-a-number
            DISMISSED_TAG${'\t'}v2.0.1
            DOWNLOADED${'\t'}v2.1.0${'\t'}../escape.apk${'\t'}10${'\t'}${"a".repeat(64)}${'\t'}1
            """.trimIndent()
        )

        assertEquals(0L, decoded.lastSuccessfulCheckEpochMillis)
        assertEquals("v2.0.1", decoded.dismissedTag)
        assertNull(decoded.downloadedUpdate)
    }

    @Test
    fun serializedStoreUsesOnlyItsOwnKey() {
        val storage = FakeStringStorage()
        val store = SerializedUpdateStateStore(storage)
        val state = UpdatePersistedState(lastSuccessfulCheckEpochMillis = 42L, lastKnownTag = "v2.1.0")

        store.save(state)

        assertEquals(setOf(SerializedUpdateStateStore.STORAGE_KEY), storage.values.keys)
        assertTrue(storage.values.getValue(SerializedUpdateStateStore.STORAGE_KEY).startsWith("UC_UPDATE_STATE_V1"))
        assertEquals(state, store.load())
    }

    private class FakeStringStorage : StringStorage {
        val values = mutableMapOf<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) {
            values[key] = value
        }
    }
}
