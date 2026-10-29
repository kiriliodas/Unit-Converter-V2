package app.unitconverter.feature.convert

import java.util.Locale

/** How the app-level light/dark theme is chosen. */
enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * An app language backed by a complete Android string-resource locale.
 *
 * The list deliberately contains only languages for which this project
 * ships a full `strings.xml`; selecting one must never produce a partly
 * translated interface. [SYSTEM] leaves Android resource resolution to
 * the phone configuration.
 */
enum class AppLanguage(
    val code: String
) {
    SYSTEM("system"),
    FRENCH("fr"),
    ENGLISH("en"),
    SPANISH("es"),
    PORTUGUESE("pt"),
    GERMAN("de"),
    ITALIAN("it"),
    CHINESE_SIMPLIFIED("zh-CN"),
    JAPANESE("ja"),
    KOREAN("ko"),
    ARABIC("ar");

    /** Locale to install in the activity configuration, or null for system. */
    fun locale(): Locale? = if (this == SYSTEM) {
        null
    } else {
        Locale.forLanguageTag(code)
    }

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

/** Small app-level preferences that must be known before first composition. */
data class AppPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM
)

interface AppPreferencesStore {
    fun load(): AppPreferences
    fun save(preferences: AppPreferences)
}

/**
 * Persists [AppPreferences] beside, but independently from, the
 * conversion library. The same app-private SharedPreferences
 * [StringStorage] is reused; keeping a separate key means theme/language
 * support cannot alter or endanger the validated `UC_LIBRARY_V1`
 * favorites/history payload.
 */
class SerializedAppPreferencesStore(
    private val storage: StringStorage,
    private val key: String = STORAGE_KEY
) : AppPreferencesStore {

    override fun load(): AppPreferences = runCatching {
        AppPreferencesSerializer.decode(storage.read(key))
    }.getOrDefault(AppPreferences())

    override fun save(preferences: AppPreferences) {
        storage.write(key, AppPreferencesSerializer.encode(preferences))
    }

    companion object {
        const val STORAGE_KEY: String = "app_preferences_v1"
    }
}

/**
 * Deliberately tiny line-oriented format. Unknown lines and unknown enum
 * values fall back independently to the system defaults, so a damaged
 * or future payload cannot make the app unusable.
 */
object AppPreferencesSerializer {
    private const val HEADER = "UC_APP_PREFERENCES_V1"
    private const val THEME_RECORD = "THEME"
    private const val LANGUAGE_RECORD = "LANGUAGE"
    private const val SEPARATOR = "\t"

    fun encode(preferences: AppPreferences): String = buildString {
        appendLine(HEADER)
        append(THEME_RECORD)
        append(SEPARATOR)
        appendLine(preferences.themeMode.name)
        append(LANGUAGE_RECORD)
        append(SEPARATOR)
        appendLine(preferences.language.code)
    }

    fun decode(serialized: String?): AppPreferences {
        if (serialized.isNullOrBlank()) return AppPreferences()
        var preferences = AppPreferences()
        serialized.lineSequence()
            .filter { it.isNotBlank() && it != HEADER }
            .forEach { line ->
                val parts = line.split(SEPARATOR)
                if (parts.size != 2) return@forEach
                when (parts[0]) {
                    THEME_RECORD -> {
                        val mode = AppThemeMode.entries.firstOrNull {
                            it.name == parts[1]
                        }
                        if (mode != null) preferences = preferences.copy(themeMode = mode)
                    }

                    LANGUAGE_RECORD -> {
                        preferences = preferences.copy(
                            language = AppLanguage.fromCode(parts[1])
                        )
                    }
                }
            }
        return preferences
    }
}
