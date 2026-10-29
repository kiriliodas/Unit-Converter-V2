package app.unitconverter.feature.convert

import app.unitconverter.core.catalog.RepresentativeCatalog
import app.unitconverter.core.conversion.DomainViolation
import app.unitconverter.core.conversion.InvalidReason
import app.unitconverter.core.model.CategoryId
import app.unitconverter.core.model.UnitId
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConverterEvaluatorTest {

    private val catalog = RepresentativeCatalog.catalog
    private val evaluator = ConverterEvaluator(catalog)

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

    private fun assertNumericEquals(expected: String, actual: BigDecimal?) {
        assertTrue("Expected a value", actual != null)
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }

    @Test
    fun emptyInputShowsEquivalentsOfOneSourceUnit() {
        val state = evaluate(inputText = "")

        assertEquals(ResultState.Empty, state.result)
        assertTrue(state.rowsUseUnitReference)
        val meterRow = state.rows.first { it.unit.id == UnitId("meter") }
        assertNumericEquals("1000", meterRow.value)
        assertEquals(state.category.units.size, state.rows.size)
    }

    @Test
    fun incompleteInputIsNeutral() {
        val state = evaluate(
            categoryId = "temperature",
            fromId = "celsius",
            toId = "fahrenheit",
            inputText = "-"
        )

        assertEquals(ResultState.Incomplete, state.result)
        assertTrue(state.rowsUseUnitReference)
    }

    @Test
    fun invalidInputMapsToInvalidState() {
        val state = evaluate(inputText = "12a")

        val result = state.result
        assertTrue(result is ResultState.Invalid)
        assertEquals(InvalidReason.INVALID_CHARACTER, (result as ResultState.Invalid).reason)
    }

    @Test
    fun validInputConvertsThroughEngine() {
        val state = evaluate(inputText = "100")

        val result = state.result
        assertTrue(result is ResultState.Value)
        val value = (result as ResultState.Value).value
        assertTrue(value.toPlainString().startsWith("62.13711922373339"))
        assertFalse(state.rowsUseUnitReference)
        // The destination row must agree with the main result.
        val mileRow = state.rows.first { it.unit.id == UnitId("mile") }
        assertEquals(0, value.compareTo(mileRow.value))
    }

    @Test
    fun commaDecimalSeparatorParses() {
        val state = evaluate(toId = "meter", inputText = "1,5")

        val result = state.result
        assertTrue(result is ResultState.Value)
        assertNumericEquals("1500", (result as ResultState.Value).value)
    }

    @Test
    fun rateIsValueOfOneSourceUnit() {
        val state = evaluate(inputText = "5")

        val result = state.result as ResultState.Value
        val rate = result.rate
        assertTrue(rate != null && rate.toPlainString().startsWith("0.62137119223733"))
    }

    @Test
    fun temperatureConversionIsExactForKelvin() {
        val state = evaluate(
            categoryId = "temperature",
            fromId = "celsius",
            toId = "kelvin",
            inputText = "-40"
        )

        val result = state.result
        assertTrue(result is ResultState.Value)
        assertNumericEquals("233.15", (result as ResultState.Value).value)
        val fahrenheitRow = state.rows.first { it.unit.id == UnitId("fahrenheit") }
        val fahrenheit = fahrenheitRow.value
        assertTrue(fahrenheit != null)
        assertTrue(fahrenheit!! > BigDecimal("-40.0001") && fahrenheit < BigDecimal("-39.9999"))
    }

    @Test
    fun belowAbsoluteZeroIsDomainError() {
        val state = evaluate(
            categoryId = "temperature",
            fromId = "celsius",
            toId = "fahrenheit",
            inputText = "-300"
        )

        val result = state.result
        assertTrue(result is ResultState.Domain)
        assertEquals(DomainViolation.BELOW_MINIMUM, (result as ResultState.Domain).violation)
    }

    @Test
    fun zeroFuelEconomyIsDomainError() {
        val state = evaluate(
            categoryId = "fuel_economy",
            fromId = "liters_per_100_kilometers",
            toId = "kilometers_per_liter",
            inputText = "0"
        )

        val result = state.result
        assertTrue(result is ResultState.Domain)
        assertEquals(DomainViolation.MUST_BE_POSITIVE, (result as ResultState.Domain).violation)
    }

    @Test
    fun negativeLengthIsDomainError() {
        val state = evaluate(fromId = "meter", toId = "kilometer", inputText = "-5")

        val result = state.result
        assertTrue(result is ResultState.Domain)
        assertEquals(
            DomainViolation.MUST_BE_NON_NEGATIVE,
            (result as ResultState.Domain).violation
        )
    }

    @Test
    fun unknownIdentifiersFallBackToDefaults() {
        val state = evaluate(
            categoryId = "no_such_category",
            fromId = "no_such_unit",
            toId = "also_missing",
            inputText = "1"
        )

        assertEquals(catalog.categories.first().id, state.category.id)
        assertEquals(state.category.defaultFromId, state.from.id)
        assertEquals(state.category.defaultToId, state.to.id)
    }

    @Test
    fun negativeSignToggleVisibilityFollowsDomain() {
        assertFalse(evaluate(inputText = "1").negativeInputAllowed)
        assertTrue(
            evaluate(
                categoryId = "temperature",
                fromId = "celsius",
                toId = "fahrenheit",
                inputText = "1"
            ).negativeInputAllowed
        )
        assertFalse(
            evaluate(
                categoryId = "fuel_economy",
                fromId = "liters_per_100_kilometers",
                toId = "kilometers_per_liter",
                inputText = "1"
            ).negativeInputAllowed
        )
    }

    @Test
    fun unavailableRowsAreNullWhenInputViolatesDomain() {
        val state = evaluate(
            categoryId = "fuel_economy",
            fromId = "liters_per_100_kilometers",
            toId = "kilometers_per_liter",
            inputText = "0"
        )

        assertTrue(state.rows.all { it.value == null })
    }
}
