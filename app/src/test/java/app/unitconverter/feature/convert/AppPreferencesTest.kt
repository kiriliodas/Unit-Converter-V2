package app.unitconverter.feature.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppPreferencesTest {
    @Test
    fun defaultsUseSystemThemeAndPhoneLanguage() {
        assertEquals(AppThemeMode.SYSTEM, AppPreferences().themeMode)
        assertEquals(AppLanguage.SYSTEM, AppPreferences().language)
        assertNull(AppLanguage.SYSTEM.locale())
        assertEquals(AppPreferences(), AppPreferencesSerializer.decode(null))
        assertEquals(AppPreferences(), AppPreferencesSerializer.decode(""))
    }

    @Test
    fun preferencesRoundTripThroughSerializerAndStore() {
        val storage = FakeStringStorage()
        val store = SerializedAppPreferencesStore(storage)
        val expected = AppPreferences(
            themeMode = AppThemeMode.DARK,
            language = AppLanguage.CHINESE_SIMPLIFIED
        )

        store.save(expected)

        assertEquals(expected, store.load())
        assertEquals(
            expected,
            AppPreferencesSerializer.decode(storage.read(SerializedAppPreferencesStore.STORAGE_KEY))
        )
    }

    @Test
    fun everySelectableLanguageHasAStablePersistedCodeAndLocale() {
        val expectedCodes = mapOf(
            AppLanguage.FRENCH to "fr",
            AppLanguage.ENGLISH to "en",
            AppLanguage.SPANISH to "es",
            AppLanguage.PORTUGUESE to "pt",
            AppLanguage.GERMAN to "de",
            AppLanguage.ITALIAN to "it",
            AppLanguage.CHINESE_SIMPLIFIED to "zh-CN",
            AppLanguage.JAPANESE to "ja",
            AppLanguage.KOREAN to "ko",
            AppLanguage.ARABIC to "ar"
        )

        expectedCodes.forEach { (language, code) ->
            assertEquals(code, language.code)
            assertEquals(language, AppLanguage.fromCode(code))
            assertEquals(code.substringBefore('-'), language.locale()?.language)
        }
    }

    @Test
    fun malformedOrFutureValuesFallBackIndependentlyToSystemDefaults() {
        val decoded = AppPreferencesSerializer.decode(
            listOf(
                "UC_APP_PREFERENCES_V1",
                "THEME\tSOLARIZED",
                "LANGUAGE\txx-YY",
                "UNKNOWN\tvalue",
                "broken-line"
            ).joinToString(separator = "\n")
        )

        assertEquals(AppPreferences(), decoded)
    }

    private class FakeStringStorage : StringStorage {
        private val values = mutableMapOf<String, String>()

        override fun read(key: String): String? = values[key]

        override fun write(key: String, value: String) {
            values[key] = value
        }
    }
}
