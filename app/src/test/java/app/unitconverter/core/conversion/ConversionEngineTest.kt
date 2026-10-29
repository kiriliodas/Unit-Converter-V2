package app.unitconverter.core.conversion

import app.unitconverter.core.catalog.RepresentativeCatalog
import app.unitconverter.core.model.CategoryId
import app.unitconverter.core.model.ConversionDefinition
import app.unitconverter.core.model.DomainRule
import app.unitconverter.core.model.UnitDefinition
import app.unitconverter.core.model.UnitId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ConversionEngineTest {

    private val catalog = RepresentativeCatalog.catalog
    private val engine = ConversionEngine()

    private fun unit(category: String, unit: String): UnitDefinition =
        catalog.requireUnit(CategoryId(category), UnitId(unit))

    private fun convert(value: String, from: UnitDefinition, to: UnitDefinition): BigDecimal =
        when (val result = engine.convert(BigDecimal(value), from, to)) {
            is ConversionResult.Success -> result.value
            else -> throw AssertionError(
                "Expected success for $value ${from.symbol} -> ${to.symbol}, got $result"
            )
        }

    private fun assertExact(expected: String, actual: BigDecimal) {
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))
    }

    private fun assertClose(expected: String, actual: BigDecimal, tolerance: String) {
        val difference = (BigDecimal(expected) - actual).abs()
        assertTrue(
            "expected $actual within $tolerance of $expected (diff $difference)",
            difference <= BigDecimal(tolerance)
        )
    }

    //region Length (linear, exact definitional factors)

    @Test
    fun `inch to centimeter is exact by definition`() {
        assertExact("2.54", convert("1", unit("length", "inch"), unit("length", "centimeter")))
    }

    @Test
    fun `mile to kilometer is exact by definition`() {
        assertExact("1.609344", convert("1", unit("length", "mile"), unit("length", "kilometer")))
    }

    @Test
    fun `known value five kilometers in miles`() {
        assertClose(
            "3.1068559611867",
            convert("5", unit("length", "kilometer"), unit("length", "mile")),
            "0.0000000000001"
        )
    }

    @Test
    fun `nautical mile and light year use their exact definitions`() {
        assertExact("1852", convert("1", unit("length", "nautical_mile"), unit("length", "meter")))
        assertExact(
            "9460730472580.8",
            convert("1", unit("length", "light_year"), unit("length", "kilometer"))
        )
    }

    @Test
    fun `negative lengths are a domain error including identity`() {
        val meter = unit("length", "meter")
        val result = engine.convert(BigDecimal("-1"), meter, meter)
        assertEquals(
            ConversionResult.DomainError(DomainViolation.MUST_BE_NON_NEGATIVE), result
        )
    }

    //endregion

    //region Temperature (affine, absolute-zero bounded)

    @Test
    fun `freezing and boiling points of water`() {
        val celsius = unit("temperature", "celsius")
        val fahrenheit = unit("temperature", "fahrenheit")
        assertExact("32", convert("0", celsius, fahrenheit))
        assertClose("212", convert("100", celsius, fahrenheit), "0.000000000000000000000000001")
        assertClose("100", convert("212", fahrenheit, celsius), "0.000000000000000000000000001")
    }

    @Test
    fun `minus forty is the same in celsius and fahrenheit`() {
        assertClose(
            "-40",
            convert("-40", unit("temperature", "celsius"), unit("temperature", "fahrenheit")),
            "0.000000000000000000000000001"
        )
    }

    @Test
    fun `absolute zero in every scale`() {
        val celsius = unit("temperature", "celsius")
        assertExact("-273.15", convert("0", unit("temperature", "kelvin"), celsius))
        assertExact("0", convert("-273.15", celsius, unit("temperature", "kelvin")))
        assertExact("-273.15", convert("0", unit("temperature", "rankine"), celsius))
        assertClose(
            "-459.67",
            convert("-273.15", celsius, unit("temperature", "fahrenheit")),
            "0.000000000000000000000000001"
        )
    }

    @Test
    fun `below absolute zero is a domain error in every scale`() {
        val celsius = unit("temperature", "celsius")
        val fahrenheit = unit("temperature", "fahrenheit")
        listOf(
            BigDecimal("-273.16") to celsius,
            BigDecimal("-459.68") to fahrenheit,
            BigDecimal("-0.1") to unit("temperature", "kelvin"),
            BigDecimal("-1") to unit("temperature", "rankine")
        ).forEach { (value, from) ->
            assertEquals(
                ConversionResult.DomainError(DomainViolation.BELOW_MINIMUM),
                engine.convert(value, from, celsius)
            )
        }
    }

    //endregion

    //region Fuel economy (reciprocal)

    @Test
    fun `liters per 100 km to kilometers per liter is a pure reciprocal`() {
        assertExact(
            "12.5",
            convert(
                "8",
                unit("fuel_economy", "liters_per_100_kilometers"),
                unit("fuel_economy", "kilometers_per_liter")
            )
        )
        assertExact(
            "5",
            convert(
                "20",
                unit("fuel_economy", "kilometers_per_liter"),
                unit("fuel_economy", "liters_per_100_kilometers")
            )
        )
    }

    @Test
    fun `mpg conversions use the correct gallon for each system`() {
        val liters = unit("fuel_economy", "liters_per_100_kilometers")
        assertClose(
            "29.401823",
            convert("8", liters, unit("fuel_economy", "miles_per_us_gallon")),
            "0.000001"
        )
        assertClose(
            "35.310117",
            convert("8", liters, unit("fuel_economy", "miles_per_imperial_gallon")),
            "0.000001"
        )
        assertClose(
            "7.840486",
            convert("30", unit("fuel_economy", "miles_per_us_gallon"), liters),
            "0.000001"
        )
        assertClose(
            "7.062023",
            convert("40", unit("fuel_economy", "miles_per_imperial_gallon"), liters),
            "0.000001"
        )
    }

    @Test
    fun `zero and negative fuel values never yield infinity`() {
        val liters = unit("fuel_economy", "liters_per_100_kilometers")
        val kmPerLiter = unit("fuel_economy", "kilometers_per_liter")
        assertEquals(
            ConversionResult.DomainError(DomainViolation.MUST_BE_POSITIVE),
            engine.convert(BigDecimal.ZERO, liters, kmPerLiter)
        )
        assertEquals(
            ConversionResult.DomainError(DomainViolation.MUST_BE_POSITIVE),
            engine.convert(BigDecimal("-8"), liters, kmPerLiter)
        )
        // Even converting a unit to itself validates its domain first.
        assertEquals(
            ConversionResult.DomainError(DomainViolation.MUST_BE_POSITIVE),
            engine.convert(BigDecimal.ZERO, liters, liters)
        )
    }

    //endregion

    //region Cross-cutting behavior

    @Test
    fun `round trips return to the starting value`() {
        fun roundTrip(value: String, from: UnitDefinition, via: UnitDefinition, tolerance: String) {
            val forward = convert(value, from, via)
            val back = engine.convert(forward, via, from)
            assertTrue(back is ConversionResult.Success)
            assertClose(value, (back as ConversionResult.Success).value, tolerance)
        }
        roundTrip(
            "123.456", unit("length", "kilometer"), unit("length", "foot"),
            "0.000000000000000001"
        )
        roundTrip(
            "37.5", unit("temperature", "celsius"), unit("temperature", "fahrenheit"),
            "0.000000000000000000000001"
        )
        roundTrip(
            "7.5",
            unit("fuel_economy", "liters_per_100_kilometers"),
            unit("fuel_economy", "miles_per_us_gallon"),
            "0.000000000000000000001"
        )
    }

    @Test
    fun `different categories are refused`() {
        assertEquals(
            ConversionResult.CategoryMismatch,
            engine.convert(
                BigDecimal.ONE,
                unit("length", "meter"),
                unit("temperature", "celsius")
            )
        )
    }

    @Test
    fun `mixed conversion shapes in one category are refused`() {
        val category = CategoryId("synthetic")
        val linear = UnitDefinition(
            id = UnitId("synthetic_linear"), categoryId = category, nameRes = 0, symbol = "sl",
            conversion = ConversionDefinition.Linear(BigDecimal.ONE),
            domain = DomainRule.AnyReal
        )
        val affine = UnitDefinition(
            id = UnitId("synthetic_affine"), categoryId = category, nameRes = 0, symbol = "sa",
            conversion = ConversionDefinition.Affine(BigDecimal.ONE, BigDecimal.ZERO),
            domain = DomainRule.AnyReal
        )
        assertEquals(
            ConversionResult.UnsupportedPair,
            engine.convert(BigDecimal.ONE, linear, affine)
        )
    }

    @Test
    fun `absurd magnitudes overflow explicitly`() {
        assertEquals(
            ConversionResult.Overflow,
            engine.convert(
                BigDecimal("1E10000"),
                unit("length", "meter"),
                unit("length", "kilometer")
            )
        )
    }

    @Test
    fun `zero converts to zero for ordinary linear units`() {
        assertExact("0", convert("0", unit("length", "kilometer"), unit("length", "mile")))
    }

    //endregion
}
