package app.unitconverter.feature.convert

import app.unitconverter.core.catalog.RepresentativeCatalog
import app.unitconverter.core.conversion.NumberFormatOptions
import app.unitconverter.core.conversion.NumberFormatter
import app.unitconverter.core.conversion.PrecisionMode
import app.unitconverter.core.model.CategoryId
import app.unitconverter.core.model.UnitId
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseC5ConversionLibraryTest {

    private val catalog = RepresentativeCatalog.catalog
    private val evaluator = ConverterEvaluator(catalog)
    private val formatter = NumberFormatter()

    private fun favorite(
        categoryId: String,
        fromId: String,
        toId: String
    ) = FavoriteConversion(CategoryId(categoryId), UnitId(fromId), UnitId(toId))

    private val pressure = favorite("pressure", "kilopascal", "pound_per_square_inch")
    private val pressureReverse =
        favorite("pressure", "pound_per_square_inch", "kilopascal")
    private val power = favorite("power", "kilowatt", "mechanical_horsepower")

    @Test
    fun pinningCreatesFavoriteUsesNewestFirstOrderAndUnpinsWithoutUnfavoriting() {
        val pinnedPressure = ConversionLibraryState().togglePinnedFavorite(pressure)
        val pinnedBoth = pinnedPressure.togglePinnedFavorite(power)
        val unpinnedPressure = pinnedBoth.togglePinnedFavorite(pressure)

        assertEquals(listOf(pressure), pinnedPressure.favorites)
        assertEquals(listOf(pressure), pinnedPressure.pinnedFavorites)
        assertEquals(listOf(power, pressure), pinnedBoth.favorites)
        assertEquals(listOf(power, pressure), pinnedBoth.pinnedFavorites)
        assertEquals(listOf(power, pressure), unpinnedPressure.favorites)
        assertEquals(listOf(power), unpinnedPressure.pinnedFavorites)
        assertTrue(unpinnedPressure.isFavorite(pressure))
        assertFalse(unpinnedPressure.isPinned(pressure))
    }

    @Test
    fun directedPairsArePinnedIndependentlyAndNeverDuplicated() {
        val state = ConversionLibraryState()
            .togglePinnedFavorite(pressure)
            .togglePinnedFavorite(pressureReverse)

        assertEquals(listOf(pressureReverse, pressure), state.pinnedFavorites)
        assertEquals(2, state.pinnedFavorites.distinct().size)

        val toggledOff = state.togglePinnedFavorite(pressure)
        assertEquals(listOf(pressureReverse), toggledOff.pinnedFavorites)
        assertTrue(toggledOff.isFavorite(pressure))
    }

    @Test
    fun removingOrTogglingOffAFavoriteAlsoRemovesItsPin() {
        val state = ConversionLibraryState()
            .togglePinnedFavorite(pressure)
            .togglePinnedFavorite(power)

        assertEquals(listOf(power), state.removeFavorite(pressure).pinnedFavorites)
        assertEquals(
            listOf(power),
            state.toggleFavorite(pressure).pinnedFavorites
        )
        assertEquals(
            listOf(power, pressure),
            state.clearHistory().pinnedFavorites
        )
    }

    @Test
    fun displaySettingsMapToDocumentedFormatterModesAndBounds() {
        assertEquals(
            PrecisionMode.Smart(10),
            DisplaySettings().toPrecisionMode()
        )
        assertEquals(
            PrecisionMode.Fixed(0),
            DisplaySettings(DisplayPrecisionMode.FIXED, 0).toPrecisionMode()
        )
        assertEquals(
            PrecisionMode.Scientific(12),
            DisplaySettings(DisplayPrecisionMode.SCIENTIFIC, 12).toPrecisionMode()
        )
        assertThrows(IllegalArgumentException::class.java) {
            DisplaySettings(DisplayPrecisionMode.FIXED, -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DisplaySettings(DisplayPrecisionMode.SCIENTIFIC, 13)
        }
    }

    @Test
    fun serializerRoundTripsPinsAndPrecisionWithoutChangingC4HeaderOrRecords() {
        val history = HistoryEntry(
            categoryId = CategoryId("pressure"),
            fromId = UnitId("kilopascal"),
            toId = UnitId("pound_per_square_inch"),
            inputText = "250",
            recordedAtEpochMillis = 1234L
        )
        val state = ConversionLibraryState()
            .togglePinnedFavorite(pressure)
            .togglePinnedFavorite(power)
            .recordHistory(history)
            .copy(
                displaySettings = DisplaySettings(
                    DisplayPrecisionMode.SCIENTIFIC,
                    decimalPlaces = 4
                )
            )

        val encoded = ConversionLibrarySerializer.encode(state)
        val decoded = ConversionLibrarySerializer.decode(encoded)

        assertEquals(state, decoded)
        assertTrue(encoded.startsWith("UC_LIBRARY_V1\nP\tSCIENTIFIC\t4\n"))
        assertTrue(
            encoded.contains(
                "F\tpressure\tkilopascal\tpound_per_square_inch\n"
            )
        )
        assertTrue(
            encoded.contains(
                "N\tpressure\tkilopascal\tpound_per_square_inch\n"
            )
        )
        assertTrue(encoded.contains("H\tpressure\tkilopascal"))
    }

    @Test
    fun phaseC4PayloadDecodesWithDefaultPrecisionAndNoPins() {
        val phaseC4Payload = """
            UC_LIBRARY_V1
            F${'\t'}pressure${'\t'}kilopascal${'\t'}pound_per_square_inch
            H${'\t'}pressure${'\t'}kilopascal${'\t'}pound_per_square_inch${'\t'}1234${'\t'}MTIuNQ
        """.trimIndent()

        val decoded = ConversionLibrarySerializer.decode(phaseC4Payload)

        assertEquals(listOf(pressure), decoded.favorites)
        assertTrue(decoded.pinnedFavorites.isEmpty())
        assertEquals(DisplaySettings(), decoded.displaySettings)
        assertEquals(1, decoded.history.size)
        assertEquals("12.5", decoded.history.single().inputText)
    }

    @Test
    fun malformedPinOrPrecisionRecordsDoNotDiscardFavoritesOrHistory() {
        val payload = """
            UC_LIBRARY_V1
            P${'\t'}NOT_A_MODE${'\t'}99
            F${'\t'}pressure${'\t'}kilopascal${'\t'}pound_per_square_inch
            N${'\t'}power${'\t'}kilowatt${'\t'}mechanical_horsepower
            N${'\t'}pressure${'\t'}kilopascal${'\t'}pound_per_square_inch
            N${'\t'}pressure${'\t'}kilopascal${'\t'}pound_per_square_inch
            H${'\t'}pressure${'\t'}kilopascal${'\t'}pound_per_square_inch${'\t'}1234${'\t'}MTIuNQ
        """.trimIndent()

        val decoded = ConversionLibrarySerializer.decode(payload)

        assertEquals(listOf(pressure), decoded.favorites)
        assertEquals(listOf(pressure), decoded.pinnedFavorites)
        assertEquals(DisplaySettings(), decoded.displaySettings)
        assertEquals(1, decoded.history.size)
    }

    @Test
    fun availableInFiltersStalePinsButPreservesDisplaySettings() {
        val stalePin = favorite("missing_category", "missing_from", "missing_to")
        val state = ConversionLibraryState(
            favorites = listOf(stalePin, pressure),
            pinnedFavorites = listOf(stalePin, pressure),
            displaySettings = DisplaySettings(
                DisplayPrecisionMode.FIXED,
                decimalPlaces = 5
            )
        )

        val available = state.availableIn(catalog)

        assertEquals(listOf(pressure), available.favorites)
        assertEquals(listOf(pressure), available.pinnedFavorites)
        assertEquals(state.displaySettings, available.displaySettings)
    }

    @Test
    fun serializedStorePersistsPinsAndPrecisionAcrossInstances() {
        var storedValue: String? = null
        val storage = object : StringStorage {
            override fun read(key: String): String? = storedValue
            override fun write(key: String, value: String) {
                storedValue = value
            }
        }
        val state = ConversionLibraryState()
            .togglePinnedFavorite(pressure)
            .copy(
                displaySettings = DisplaySettings(
                    DisplayPrecisionMode.FIXED,
                    decimalPlaces = 3
                )
            )

        SerializedConversionLibraryStore(storage).save(state)

        assertEquals(state, SerializedConversionLibraryStore(storage).load())
    }

    @Test
    fun swappingUnitsKeepsInputAndCategoryAndRecalculatesImmediately() {
        val original = evaluator.evaluate(
            categoryId = CategoryId("speed"),
            fromId = UnitId("kilometer_per_hour"),
            toId = UnitId("mile_per_hour"),
            inputText = "100"
        )
        val swapped = evaluator.evaluate(
            categoryId = original.category.id,
            fromId = original.to.id,
            toId = original.from.id,
            inputText = original.inputText
        )

        assertEquals(original.category.id, swapped.category.id)
        assertEquals(original.to.id, swapped.from.id)
        assertEquals(original.from.id, swapped.to.id)
        assertEquals("100", swapped.inputText)
        val swappedValue = (swapped.result as ResultState.Value).value
        assertEquals(0, BigDecimal("160.9344").compareTo(swappedValue))
    }

    @Test
    fun pinnedPairRestoresOnlyUnitsSoCurrentInputRemainsFreelyEditable() {
        fun evaluatePinned(inputText: String) = evaluator.evaluate(
            categoryId = pressure.categoryId,
            fromId = pressure.fromId,
            toId = pressure.toId,
            inputText = inputText
        )

        val first = evaluatePinned("250")
        val second = evaluatePinned("500")

        assertEquals("250", first.inputText)
        assertEquals("500", second.inputText)
        val firstValue = (first.result as ResultState.Value).value
        val secondValue = (second.result as ResultState.Value).value
        val doublingDifference =
            secondValue.subtract(firstValue.multiply(BigDecimal("2"))).abs()
        assertTrue(doublingDifference <= BigDecimal("0.000000000001"))
    }

    @Test
    fun displayFormattingChangesOnlyTextNotTheEvaluatorsBigDecimal() {
        val state = evaluator.evaluate(
            categoryId = CategoryId("speed"),
            fromId = UnitId("kilometer_per_hour"),
            toId = UnitId("mile_per_hour"),
            inputText = "100"
        )
        val value = (state.result as ResultState.Value).value
        val originalText = value.toPlainString()

        assertEquals(
            "62",
            formatter.format(
                value,
                NumberFormatOptions(precision = PrecisionMode.Fixed(0))
            )
        )
        assertEquals(originalText, value.toPlainString())
        assertTrue(value.compareTo(BigDecimal("62")) != 0)
    }
}
