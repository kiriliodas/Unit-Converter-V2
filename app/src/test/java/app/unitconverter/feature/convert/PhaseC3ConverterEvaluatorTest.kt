package app.unitconverter.feature.convert

import app.unitconverter.core.catalog.RepresentativeCatalog
import app.unitconverter.core.conversion.DomainViolation
import app.unitconverter.core.model.CategoryId
import app.unitconverter.core.model.UnitId
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseC3ConverterEvaluatorTest {

    private val catalog = RepresentativeCatalog.catalog
    private val evaluator = ConverterEvaluator(catalog)

    private fun evaluate(
        categoryId: String,
        fromId: String,
        toId: String,
        inputText: String
    ): ConverterUiState = evaluator.evaluate(
        categoryId = CategoryId(categoryId),
        fromId = UnitId(fromId),
        toId = UnitId(toId),
        inputText = inputText
    )

    @Test
    fun everyCatalogCategoryEvaluatesItsDefaultPairAndAllRows() {
        assertEquals(17, catalog.categories.size)
        catalog.categories.forEach { category ->
            val state = evaluator.evaluate(
                categoryId = category.id,
                fromId = category.defaultFromId,
                toId = category.defaultToId,
                inputText = "1"
            )
            assertEquals(category.id, state.category.id)
            assertTrue(state.result is ResultState.Value)
            assertEquals(category.units.size, state.rows.size)
            assertFalse(state.rowsUseUnitReference)
        }
    }

    @Test
    fun digitalStorageStateKeepsDecimalAndBinaryRowsDistinct() {
        val state = evaluate("data_storage", "megabyte", "mebibyte", "1")

        val result = state.result as ResultState.Value
        assertEquals(0, BigDecimal("0.95367431640625").compareTo(result.value))
        val byteRow = state.rows.first { it.unit.id == UnitId("byte") }
        assertEquals(0, BigDecimal("1000000").compareTo(byteRow.value))
        val bitRow = state.rows.first { it.unit.id == UnitId("bit") }
        assertEquals(0, BigDecimal("8000000").compareTo(bitRow.value))
        assertFalse(state.negativeInputAllowed)
    }

    @Test
    fun dataRateStateConvertsMegabitsToMegabytes() {
        val state = evaluate("data_rate", "megabit_per_second", "megabyte_per_second", "100")

        val result = state.result as ResultState.Value
        assertEquals(0, BigDecimal("12.5").compareTo(result.value))
        assertEquals(8, state.rows.size)
        assertFalse(state.negativeInputAllowed)
    }

    @Test
    fun emptyVolumeInputShowsEquivalentsOfOneLiter() {
        val state = evaluate("volume", "liter", "us_gallon", "")

        assertEquals(ResultState.Empty, state.result)
        assertTrue(state.rowsUseUnitReference)
        val cupRow = state.rows.first { it.unit.id == UnitId("us_cup") }
        val cups = cupRow.value
        assertTrue(cups != null)
        assertTrue(cups!! > BigDecimal("4.22675") && cups < BigDecimal("4.22676"))
    }

    @Test
    fun negativeMassAndNegativeSpeedUseExistingDomainHandling() {
        val mass = evaluate("mass", "kilogram", "pound", "-1")
        assertTrue(mass.result is ResultState.Domain)
        assertEquals(
            DomainViolation.MUST_BE_NON_NEGATIVE,
            (mass.result as ResultState.Domain).violation
        )

        val speed = evaluate("speed", "kilometer_per_hour", "mile_per_hour", "-1")
        assertTrue(speed.result is ResultState.Domain)
        assertEquals(
            DomainViolation.MUST_BE_NON_NEGATIVE,
            (speed.result as ResultState.Domain).violation
        )
        assertFalse(speed.negativeInputAllowed)
    }
}
