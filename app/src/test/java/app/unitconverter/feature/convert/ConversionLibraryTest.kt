package app.unitconverter.feature.convert

import app.unitconverter.core.catalog.RepresentativeCatalog
import app.unitconverter.core.model.CategoryId
import app.unitconverter.core.model.UnitId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversionLibraryTest {

    private val catalog = RepresentativeCatalog.catalog
    private val evaluator = ConverterEvaluator(catalog)

    private fun favorite(
        categoryId: String = "length",
        fromId: String = "kilometer",
        toId: String = "mile"
    ) = FavoriteConversion(CategoryId(categoryId), UnitId(fromId), UnitId(toId))

    private fun entry(
        inputText: String,
        timestamp: Long,
        categoryId: String = "length",
        fromId: String = "kilometer",
        toId: String = "mile"
    ) = HistoryEntry(
        categoryId = CategoryId(categoryId),
        fromId = UnitId(fromId),
        toId = UnitId(toId),
        inputText = inputText,
        recordedAtEpochMillis = timestamp
    )

    private fun evaluate(
        categoryId: String = "length",
        fromId: String = "kilometer",
        toId: String = "mile",
        inputText: String
    ): ConverterUiState = evaluator.evaluate(
        categoryId = CategoryId(categoryId),
        fromId = UnitId(fromId),
        toId = UnitId(toId),
        inputText = inputText
    )

    @Test
    fun favoriteToggleAddsNewestFirstDeduplicatesAndRemoves() {
        val length = favorite()
        val mass = favorite("mass", "kilogram", "pound")

        val withLength = ConversionLibraryState().toggleFavorite(length)
        val withBoth = withLength.toggleFavorite(mass)
        val withoutLength = withBoth.toggleFavorite(length)

        assertEquals(listOf(length), withLength.favorites)
        assertEquals(listOf(mass, length), withBoth.favorites)
        assertEquals(listOf(mass), withoutLength.favorites)
        assertTrue(withBoth.isFavorite(length))
        assertFalse(withoutLength.isFavorite(length))
        assertSame(withoutLength, withoutLength.removeFavorite(length))
        assertEquals(emptyList<FavoriteConversion>(), withoutLength.removeFavorite(mass).favorites)
    }

    @Test
    fun recordingSameHistoryConversionRefreshesInsteadOfDuplicating() {
        val first = entry("10", timestamp = 10L)
        val second = entry("20", timestamp = 20L)
        val firstAgain = entry(" 10 ", timestamp = 30L)

        val state = ConversionLibraryState()
            .recordHistory(first)
            .recordHistory(second)
            .recordHistory(firstAgain)

        assertEquals(2, state.history.size)
        assertEquals("10", state.history[0].inputText)
        assertEquals(30L, state.history[0].recordedAtEpochMillis)
        assertEquals(second, state.history[1])
    }

    @Test
    fun historyIsNewestFirstAndBoundedToFiftyDistinctEntries() {
        var state = ConversionLibraryState()
        for (index in 1..55) {
            state = state.recordHistory(
                entry(index.toString(), timestamp = index.toLong())
            )
        }

        assertEquals(ConversionLibraryState.MAX_HISTORY_SIZE, state.history.size)
        assertEquals("55", state.history.first().inputText)
        assertEquals("6", state.history.last().inputText)
        assertTrue(
            state.history.zipWithNext().all { (newer, older) ->
                newer.recordedAtEpochMillis > older.recordedAtEpochMillis
            }
        )
    }

    @Test
    fun clearHistoryPreservesFavorites() {
        val favorite = favorite()
        val state = ConversionLibraryState()
            .toggleFavorite(favorite)
            .recordHistory(entry("1", timestamp = 1L))

        val cleared = state.clearHistory()

        assertEquals(listOf(favorite), cleared.favorites)
        assertTrue(cleared.history.isEmpty())
        assertSame(cleared, cleared.clearHistory())
    }

    @Test
    fun onlySuccessfulCompletedConversionsAreRecorded() {
        val empty = ConversionLibraryState()

        val valid = evaluate(inputText = "1,5")
        val recorded = empty.recordCompletedConversion(valid, recordedAtEpochMillis = 123L)
        assertEquals(1, recorded.history.size)
        assertEquals(
            entry("1,5", timestamp = 123L),
            recorded.history.single()
        )

        assertSame(empty, empty.recordCompletedConversion(evaluate(inputText = ""), 1L))
        assertSame(
            empty,
            empty.recordCompletedConversion(
                evaluate(
                    categoryId = "temperature",
                    fromId = "celsius",
                    toId = "fahrenheit",
                    inputText = "-"
                ),
                2L
            )
        )
        assertSame(
            empty,
            empty.recordCompletedConversion(evaluate(inputText = "12a"), 3L)
        )
        assertSame(
            empty,
            empty.recordCompletedConversion(
                evaluate(
                    categoryId = "fuel_economy",
                    fromId = "liters_per_100_kilometers",
                    toId = "kilometers_per_liter",
                    inputText = "0"
                ),
                4L
            )
        )
    }

    @Test
    fun serializerRoundTripRestoresFavoritesHistoryAndInput() {
        val state = ConversionLibraryState()
            .toggleFavorite(favorite())
            .toggleFavorite(favorite("data_storage", "megabyte", "mebibyte"))
            .recordHistory(entry("1 000,5", timestamp = 100L))
            .recordHistory(
                entry(
                    inputText = "100",
                    timestamp = 200L,
                    categoryId = "speed",
                    fromId = "kilometer_per_hour",
                    toId = "mile_per_hour"
                )
            )

        val decoded = ConversionLibrarySerializer.decode(
            ConversionLibrarySerializer.encode(state)
        )

        assertEquals(state, decoded)
        val restored = decoded.history.first()
        assertEquals(CategoryId("speed"), restored.categoryId)
        assertEquals(UnitId("kilometer_per_hour"), restored.fromId)
        assertEquals(UnitId("mile_per_hour"), restored.toId)
        assertEquals("100", restored.inputText)
    }

    @Test
    fun restoredHistoryEntryEvaluatesWithItsSavedCategoryUnitsAndInput() {
        val saved = entry(
            inputText = "100",
            timestamp = 200L,
            categoryId = "speed",
            fromId = "kilometer_per_hour",
            toId = "mile_per_hour"
        )
        val decoded = ConversionLibrarySerializer.decode(
            ConversionLibrarySerializer.encode(
                ConversionLibraryState().recordHistory(saved)
            )
        ).history.single()

        val restoredState = evaluator.evaluate(
            categoryId = decoded.categoryId,
            fromId = decoded.fromId,
            toId = decoded.toId,
            inputText = decoded.inputText
        )

        assertEquals(CategoryId("speed"), restoredState.category.id)
        assertEquals(UnitId("kilometer_per_hour"), restoredState.from.id)
        assertEquals(UnitId("mile_per_hour"), restoredState.to.id)
        assertEquals("100", restoredState.inputText)
        assertTrue(restoredState.result is ResultState.Value)
    }

    @Test
    fun decoderIgnoresMalformedLinesSortsAndDeduplicatesHistory() {
        val encoded = listOf(
            "UC_LIBRARY_V1",
            "F\tlength\tkilometer\tmile",
            "F\tlength\tkilometer\tmile",
            "F\tINVALID\tkilometer\tmile",
            "H\tlength\tkilometer\tmile\t10\tMQ",
            "H\tlength\tkilometer\tmile\t20\tMQ",
            "H\tlength\tkilometer\tmile\tnope\tMQ",
            "not a record"
        ).joinToString(separator = "\n")

        val decoded = ConversionLibrarySerializer.decode(encoded)

        assertEquals(listOf(favorite()), decoded.favorites)
        assertEquals(listOf(entry("1", timestamp = 20L)), decoded.history)
    }

    @Test
    fun serializedStorePersistsAcrossStoreInstances() {
        val storage = FakeStringStorage()
        val state = ConversionLibraryState()
            .toggleFavorite(favorite())
            .recordHistory(entry("42", timestamp = 42L))

        SerializedConversionLibraryStore(storage).save(state)
        val loaded = SerializedConversionLibraryStore(storage).load()

        assertEquals(state, loaded)
        assertTrue(
            storage.values.getValue(SerializedConversionLibraryStore.STORAGE_KEY)
                .startsWith("UC_LIBRARY_V1")
        )
    }

    @Test
    fun unreadablePersistentStorageFallsBackToAnEmptyLibrary() {
        val storage = object : StringStorage {
            override fun read(key: String): String? =
                throw IllegalStateException("unreadable")

            override fun write(key: String, value: String) = Unit
        }

        assertEquals(
            ConversionLibraryState(),
            SerializedConversionLibraryStore(storage).load()
        )
    }

    @Test
    fun staleCatalogEntriesCanBeFilteredBeforeDisplayOrRestore() {
        val missingFavorite = favorite("missing_category", "missing_from", "missing_to")
        val validFavorite = favorite()
        val state = ConversionLibraryState(
            favorites = listOf(missingFavorite, validFavorite),
            history = listOf(
                entry(
                    inputText = "1",
                    timestamp = 20L,
                    categoryId = "missing_category",
                    fromId = "missing_from",
                    toId = "missing_to"
                ),
                entry("2", timestamp = 10L)
            )
        )

        val available = state.availableIn(catalog)

        assertEquals(listOf(validFavorite), available.favorites)
        assertEquals(listOf(entry("2", timestamp = 10L)), available.history)
    }

    private class FakeStringStorage : StringStorage {
        val values = mutableMapOf<String, String>()

        override fun read(key: String): String? = values[key]

        override fun write(key: String, value: String) {
            values[key] = value
        }
    }
}
