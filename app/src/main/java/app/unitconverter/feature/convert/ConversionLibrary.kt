package app.unitconverter.feature.convert

import app.unitconverter.core.catalog.UnitCatalog
import app.unitconverter.core.conversion.PrecisionMode
import app.unitconverter.core.model.CategoryId
import app.unitconverter.core.model.UnitId
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * A favorite conversion is one directed unit pair inside one category.
 * The input value is deliberately not part of a favorite: restoring it
 * keeps whatever value is currently being edited in the converter.
 */
data class FavoriteConversion(
    val categoryId: CategoryId,
    val fromId: UnitId,
    val toId: UnitId
)

/**
 * One completed conversion saved in history.
 *
 * History uses the product's copy-to-save rule: entries are created by
 * the explicit Copy action for a valid result, not by typing, timers,
 * navigation, or recomposition. [inputText] is stored in a form that
 * can be restored directly into the converter input.
 */
data class HistoryEntry(
    val categoryId: CategoryId,
    val fromId: UnitId,
    val toId: UnitId,
    val inputText: String,
    val recordedAtEpochMillis: Long
) {
    init {
        require(inputText.isNotBlank()) { "History input must not be blank" }
        require(recordedAtEpochMillis >= 0L) { "History timestamp must not be negative" }
    }
}

/** User-facing display precision choices exposed by Phase C5. */
enum class DisplayPrecisionMode {
    SMART,
    FIXED,
    SCIENTIFIC
}

/**
 * Persisted display-only formatting preference.
 *
 * Smart mode keeps the established ten-significant-figure presentation.
 * Fixed and Scientific modes use [decimalPlaces] in the documented
 * `0..12` range. Changing this value only changes how a conversion
 * result is rendered or copied; it never changes the [java.math.BigDecimal]
 * returned by the conversion engine.
 */
data class DisplaySettings(
    val mode: DisplayPrecisionMode = DisplayPrecisionMode.SMART,
    val decimalPlaces: Int = DEFAULT_DECIMAL_PLACES
) {
    init {
        require(decimalPlaces in 0..MAX_DECIMAL_PLACES) {
            "Display decimal places must be in 0..$MAX_DECIMAL_PLACES"
        }
    }

    fun toPrecisionMode(): PrecisionMode = when (mode) {
        DisplayPrecisionMode.SMART -> PrecisionMode.Smart(SMART_SIGNIFICANT_FIGURES)
        DisplayPrecisionMode.FIXED -> PrecisionMode.Fixed(decimalPlaces)
        DisplayPrecisionMode.SCIENTIFIC -> PrecisionMode.Scientific(decimalPlaces)
    }

    companion object {
        const val DEFAULT_DECIMAL_PLACES: Int = 2
        const val MAX_DECIMAL_PLACES: Int = 12
        const val SMART_SIGNIFICANT_FIGURES: Int = 10
    }
}

/**
 * Immutable favorites/history state.
 *
 * Favorites are unique directed pairs, newest first. Pinned favorites
 * are a unique newest-pinned-first subset of favorites used for quick
 * access on the main screen. History is unique by (category, source,
 * destination, input), newest first, and bounded to
 * [MAX_HISTORY_SIZE] entries. [displaySettings] is stored with the
 * library so Phase C5 can persist it through the same small private
 * SharedPreferences payload without introducing another store.
 */
data class ConversionLibraryState(
    val favorites: List<FavoriteConversion> = emptyList(),
    val pinnedFavorites: List<FavoriteConversion> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val displaySettings: DisplaySettings = DisplaySettings()
) {
    init {
        require(favorites.distinct().size == favorites.size) {
            "Duplicate favorites are not allowed"
        }
        require(pinnedFavorites.distinct().size == pinnedFavorites.size) {
            "Duplicate pinned favorites are not allowed"
        }
        require(pinnedFavorites.all { it in favorites }) {
            "Pinned favorites must be favorites"
        }
        require(history.size <= MAX_HISTORY_SIZE) {
            "History exceeds the maximum size"
        }
        require(history.distinctBy { it.distinctKey() }.size == history.size) {
            "Duplicate history entries are not allowed"
        }
        require(
            history.zipWithNext().all { (newer, older) ->
                newer.recordedAtEpochMillis >= older.recordedAtEpochMillis
            }
        ) { "History must be newest first" }
    }

    fun isFavorite(favorite: FavoriteConversion): Boolean = favorite in favorites

    fun toggleFavorite(favorite: FavoriteConversion): ConversionLibraryState =
        if (favorite in favorites) {
            copy(
                favorites = favorites.filterNot { it == favorite },
                pinnedFavorites = pinnedFavorites.filterNot { it == favorite }
            )
        } else {
            copy(favorites = listOf(favorite) + favorites)
        }

    fun removeFavorite(favorite: FavoriteConversion): ConversionLibraryState =
        if (favorite in favorites) {
            copy(
                favorites = favorites.filterNot { it == favorite },
                pinnedFavorites = pinnedFavorites.filterNot { it == favorite }
            )
        } else {
            this
        }

    fun isPinned(favorite: FavoriteConversion): Boolean = favorite in pinnedFavorites

    /**
     * Pins a directed pair for main-screen quick access. Pinning a pair
     * that is not yet a favorite also adds it to favorites, so a pin
     * always remains manageable and removable from the Favorites view.
     */
    fun togglePinnedFavorite(favorite: FavoriteConversion): ConversionLibraryState =
        if (favorite in pinnedFavorites) {
            copy(pinnedFavorites = pinnedFavorites.filterNot { it == favorite })
        } else {
            copy(
                favorites = if (favorite in favorites) {
                    favorites
                } else {
                    listOf(favorite) + favorites
                },
                pinnedFavorites = listOf(favorite) + pinnedFavorites
            )
        }

    fun recordHistory(entry: HistoryEntry): ConversionLibraryState {
        val normalizedEntry = entry.copy(inputText = entry.inputText.trim())
        val withoutDuplicate = history.filterNot {
            it.distinctKey() == normalizedEntry.distinctKey()
        }
        return copy(
            history = (listOf(normalizedEntry) + withoutDuplicate)
                .sortedByDescending { it.recordedAtEpochMillis }
                .take(MAX_HISTORY_SIZE)
        )
    }

    /**
     * Records [state] only when it represents a successfully completed
     * conversion. Empty, incomplete, invalid, domain-invalid, and
     * overflowing states leave this state instance unchanged.
     */
    fun recordCompletedConversion(
        state: ConverterUiState,
        recordedAtEpochMillis: Long
    ): ConversionLibraryState {
        if (state.result !is ResultState.Value) return this
        return recordHistory(
            HistoryEntry(
                categoryId = state.category.id,
                fromId = state.from.id,
                toId = state.to.id,
                inputText = state.inputText,
                recordedAtEpochMillis = recordedAtEpochMillis
            )
        )
    }

    fun clearHistory(): ConversionLibraryState =
        if (history.isEmpty()) this else copy(history = emptyList())

    /** Returns only entries whose stable IDs still exist in [catalog]. */
    fun availableIn(catalog: UnitCatalog): ConversionLibraryState = copy(
        favorites = favorites.filter { it.isAvailableIn(catalog) },
        pinnedFavorites = pinnedFavorites.filter { it.isAvailableIn(catalog) },
        history = history.filter { it.isAvailableIn(catalog) }
    )

    companion object {
        const val MAX_HISTORY_SIZE: Int = 50
    }
}

fun FavoriteConversion.isAvailableIn(catalog: UnitCatalog): Boolean =
    catalog.unit(categoryId, fromId) != null && catalog.unit(categoryId, toId) != null

fun HistoryEntry.isAvailableIn(catalog: UnitCatalog): Boolean =
    catalog.unit(categoryId, fromId) != null && catalog.unit(categoryId, toId) != null

private fun HistoryEntry.distinctKey(): List<String> = listOf(
    categoryId.value,
    fromId.value,
    toId.value,
    inputText.trim()
)

/** Minimal storage abstraction needed by the serialized library store. */
interface StringStorage {
    fun read(key: String): String?
    fun write(key: String, value: String)
}

interface ConversionLibraryStore {
    fun load(): ConversionLibraryState
    fun save(state: ConversionLibraryState)
}

/**
 * Persists the whole small library as one versioned string in a
 * [StringStorage]. This keeps the storage format and all validation in
 * pure Kotlin; the Android adapter only supplies SharedPreferences.
 */
class SerializedConversionLibraryStore(
    private val storage: StringStorage,
    private val key: String = STORAGE_KEY
) : ConversionLibraryStore {

    override fun load(): ConversionLibraryState = runCatching {
        ConversionLibrarySerializer.decode(storage.read(key))
    }.getOrDefault(ConversionLibraryState())

    override fun save(state: ConversionLibraryState) {
        storage.write(key, ConversionLibrarySerializer.encode(state))
    }

    companion object {
        const val STORAGE_KEY: String = "conversion_library_v1"
    }
}

/**
 * Stable, line-oriented storage format:
 *
 * ```
 * UC_LIBRARY_V1
 * P<TAB>displayMode<TAB>decimalPlaces
 * F<TAB>category<TAB>from<TAB>to
 * N<TAB>category<TAB>from<TAB>to
 * H<TAB>category<TAB>from<TAB>to<TAB>epochMillis<TAB>base64Url(input)
 * ```
 *
 * The V1 header and all Phase C4 `F`/`H` records are unchanged. Phase
 * C5 only appends optional `P` (precision) and `N` (pinned favorite)
 * record types: a C4 payload therefore decodes with default display
 * settings and no pins, while a C4 decoder ignores the new records and
 * still reads every favorite and history entry. Pin records are
 * accepted only when the same directed pair is also decoded as a
 * favorite, preserving the state invariant for hand-edited payloads.
 *
 * IDs already use a restricted persistence-safe alphabet. The input is
 * Base64url-encoded so pasted separators or whitespace in a numeric
 * value can never corrupt the surrounding records. Decoding ignores
 * malformed lines instead of failing the whole library.
 */
object ConversionLibrarySerializer {

    private const val HEADER = "UC_LIBRARY_V1"
    private const val DISPLAY_SETTINGS_RECORD = "P"
    private const val FAVORITE_RECORD = "F"
    private const val PINNED_FAVORITE_RECORD = "N"
    private const val HISTORY_RECORD = "H"
    private const val SEPARATOR = "\t"

    fun encode(state: ConversionLibraryState): String = buildString {
        appendLine(HEADER)
        append(DISPLAY_SETTINGS_RECORD)
        append(SEPARATOR)
        append(state.displaySettings.mode.name)
        append(SEPARATOR)
        appendLine(state.displaySettings.decimalPlaces)
        state.favorites.forEach { favorite ->
            appendPairRecord(FAVORITE_RECORD, favorite)
        }
        state.pinnedFavorites.forEach { favorite ->
            appendPairRecord(PINNED_FAVORITE_RECORD, favorite)
        }
        state.history.forEach { entry ->
            append(HISTORY_RECORD)
            append(SEPARATOR)
            append(entry.categoryId.value)
            append(SEPARATOR)
            append(entry.fromId.value)
            append(SEPARATOR)
            append(entry.toId.value)
            append(SEPARATOR)
            append(entry.recordedAtEpochMillis)
            append(SEPARATOR)
            appendLine(encodeInput(entry.inputText))
        }
    }

    private fun StringBuilder.appendPairRecord(
        recordType: String,
        favorite: FavoriteConversion
    ) {
        append(recordType)
        append(SEPARATOR)
        append(favorite.categoryId.value)
        append(SEPARATOR)
        append(favorite.fromId.value)
        append(SEPARATOR)
        appendLine(favorite.toId.value)
    }

    fun decode(serialized: String?): ConversionLibraryState {
        if (serialized.isNullOrBlank()) return ConversionLibraryState()

        var displaySettings = DisplaySettings()
        val favorites = mutableListOf<FavoriteConversion>()
        val pinnedFavorites = mutableListOf<FavoriteConversion>()
        val history = mutableListOf<HistoryEntry>()
        serialized.lineSequence()
            .filter { it.isNotBlank() && it != HEADER }
            .forEach { line ->
                val parts = line.split(SEPARATOR)
                when (parts.firstOrNull()) {
                    DISPLAY_SETTINGS_RECORD -> decodeDisplaySettings(parts)?.let { settings ->
                        displaySettings = settings
                    }

                    FAVORITE_RECORD -> decodeFavorite(parts)?.let { favorite ->
                        if (favorite !in favorites) favorites += favorite
                    }

                    PINNED_FAVORITE_RECORD -> decodeFavorite(parts)?.let { favorite ->
                        if (favorite !in pinnedFavorites) pinnedFavorites += favorite
                    }

                    HISTORY_RECORD -> decodeHistory(parts)?.let { entry ->
                        history += entry
                    }
                }
            }

        val normalizedHistory = history
            .sortedByDescending { it.recordedAtEpochMillis }
            .distinctBy { it.distinctKey() }
            .take(ConversionLibraryState.MAX_HISTORY_SIZE)
        return ConversionLibraryState(
            favorites = favorites,
            pinnedFavorites = pinnedFavorites.filter { it in favorites },
            history = normalizedHistory,
            displaySettings = displaySettings
        )
    }

    private fun decodeDisplaySettings(parts: List<String>): DisplaySettings? {
        if (parts.size != 3) return null
        return runCatching {
            DisplaySettings(
                mode = DisplayPrecisionMode.valueOf(parts[1]),
                decimalPlaces = parts[2].toInt()
            )
        }.getOrNull()
    }

    private fun decodeFavorite(parts: List<String>): FavoriteConversion? {
        if (parts.size != 4) return null
        return runCatching {
            FavoriteConversion(
                categoryId = CategoryId(parts[1]),
                fromId = UnitId(parts[2]),
                toId = UnitId(parts[3])
            )
        }.getOrNull()
    }

    private fun decodeHistory(parts: List<String>): HistoryEntry? {
        if (parts.size != 6) return null
        return runCatching {
            HistoryEntry(
                categoryId = CategoryId(parts[1]),
                fromId = UnitId(parts[2]),
                toId = UnitId(parts[3]),
                inputText = decodeInput(parts[5]),
                recordedAtEpochMillis = parts[4].toLong()
            )
        }.getOrNull()
    }

    private fun encodeInput(input: String): String =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(input.toByteArray(StandardCharsets.UTF_8))

    private fun decodeInput(encoded: String): String =
        String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
}
