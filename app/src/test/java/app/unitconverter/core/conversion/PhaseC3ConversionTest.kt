package app.unitconverter.core.conversion

import app.unitconverter.core.catalog.RepresentativeCatalog
import app.unitconverter.core.model.CategoryId
import app.unitconverter.core.model.UnitDefinition
import app.unitconverter.core.model.UnitId
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseC3ConversionTest {

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

    @Test
    fun `mass uses exact SI and avoirdupois definitions`() {
        assertExact("0.45359237", convert("1", unit("mass", "pound"), unit("mass", "kilogram")))
        assertExact("28.349523125", convert("1", unit("mass", "ounce"), unit("mass", "gram")))
        assertExact("16", convert("1", unit("mass", "pound"), unit("mass", "ounce")))
        assertExact("1000", convert("1", unit("mass", "metric_tonne"), unit("mass", "kilogram")))
        assertExact("1000000000", convert("1", unit("mass", "kilogram"), unit("mass", "microgram")))
        assertClose(
            "2204.6226218487757",
            convert("1", unit("mass", "metric_tonne"), unit("mass", "pound")),
            "0.000000000001"
        )
    }

    @Test
    fun `area uses exact hectare acre and square foot definitions`() {
        assertExact("10000", convert("1", unit("area", "hectare"), unit("area", "square_meter")))
        assertExact("4046.8564224", convert("1", unit("area", "acre"), unit("area", "square_meter")))
        assertExact("0.09290304", convert("1", unit("area", "square_foot"), unit("area", "square_meter")))
        assertExact("43560", convert("1", unit("area", "acre"), unit("area", "square_foot")))
        assertExact("100", convert("1", unit("area", "square_kilometer"), unit("area", "hectare")))
        assertClose(
            "10.763910416709722",
            convert("1", unit("area", "square_meter"), unit("area", "square_foot")),
            "0.000000000000001"
        )
    }

    @Test
    fun `volume uses exact gallons and US customary culinary measures`() {
        assertExact("3.785411784", convert("1", unit("volume", "us_gallon"), unit("volume", "liter")))
        assertExact("4.54609", convert("1", unit("volume", "imperial_gallon"), unit("volume", "liter")))
        assertExact("236.5882365", convert("1", unit("volume", "us_cup"), unit("volume", "milliliter")))
        assertExact("4.92892159375", convert("1", unit("volume", "us_teaspoon"), unit("volume", "milliliter")))
        assertExact("14.78676478125", convert("1", unit("volume", "us_tablespoon"), unit("volume", "milliliter")))
        assertExact("3", convert("1", unit("volume", "us_tablespoon"), unit("volume", "us_teaspoon")))
        assertExact("16", convert("1", unit("volume", "us_cup"), unit("volume", "us_tablespoon")))
        assertExact("1000", convert("1", unit("volume", "cubic_meter"), unit("volume", "liter")))
        assertExact("100", convert("1", unit("volume", "liter"), unit("volume", "centiliter")))
    }

    @Test
    fun `speed uses exact mile nautical mile and hour ratios`() {
        assertClose(
            "3.6",
            convert("1", unit("speed", "meter_per_second"), unit("speed", "kilometer_per_hour")),
            "0.000000000000000000001"
        )
        assertClose(
            "1.609344",
            convert("1", unit("speed", "mile_per_hour"), unit("speed", "kilometer_per_hour")),
            "0.000000000000000000001"
        )
        assertClose(
            "62.137119223733397",
            convert("100", unit("speed", "kilometer_per_hour"), unit("speed", "mile_per_hour")),
            "0.000000000000001"
        )
        assertClose(
            "1.852",
            convert("1", unit("speed", "knot"), unit("speed", "kilometer_per_hour")),
            "0.000000000000000000001"
        )
        assertClose(
            "0.5144444444444445",
            convert("1", unit("speed", "knot"), unit("speed", "meter_per_second")),
            "0.000000000000001"
        )
        assertClose(
            "0.6818181818181818",
            convert("1", unit("speed", "foot_per_second"), unit("speed", "mile_per_hour")),
            "0.000000000000001"
        )
    }

    @Test
    fun `time uses fixed minute hour day and week durations`() {
        assertExact("3600000", convert("1", unit("time", "hour"), unit("time", "millisecond")))
        assertExact("86400", convert("1", unit("time", "day"), unit("time", "second")))
        assertExact("168", convert("1", unit("time", "week"), unit("time", "hour")))
        assertExact("1.5", convert("90", unit("time", "minute"), unit("time", "hour")))
        assertExact("604800", convert("1", unit("time", "week"), unit("time", "second")))
    }

    @Test
    fun `digital storage distinguishes bit byte decimal and binary prefixes`() {
        assertExact("8", convert("1", unit("data_storage", "byte"), unit("data_storage", "bit")))
        assertExact("0.125", convert("1", unit("data_storage", "bit"), unit("data_storage", "byte")))
        assertExact("1000", convert("1", unit("data_storage", "kilobyte"), unit("data_storage", "byte")))
        assertExact("1024", convert("1", unit("data_storage", "kibibyte"), unit("data_storage", "byte")))
        assertExact("1.024", convert("1", unit("data_storage", "kibibyte"), unit("data_storage", "kilobyte")))
        assertExact(
            "0.95367431640625",
            convert("1", unit("data_storage", "megabyte"), unit("data_storage", "mebibyte"))
        )
        assertExact("1024", convert("1", unit("data_storage", "gibibyte"), unit("data_storage", "mebibyte")))
        assertExact("1000", convert("1", unit("data_storage", "terabyte"), unit("data_storage", "gigabyte")))
        assertExact(
            "1099511627776",
            convert("1", unit("data_storage", "tebibyte"), unit("data_storage", "byte"))
        )
        assertExact("8000000000", convert("1", unit("data_storage", "gigabyte"), unit("data_storage", "bit")))
    }

    @Test
    fun `data rate uses decimal prefixes and eight bits per byte`() {
        assertExact("1000", convert("1", unit("data_rate", "kilobit_per_second"), unit("data_rate", "bit_per_second")))
        assertExact("8", convert("1", unit("data_rate", "byte_per_second"), unit("data_rate", "bit_per_second")))
        assertExact("8", convert("1", unit("data_rate", "megabyte_per_second"), unit("data_rate", "megabit_per_second")))
        assertExact("125", convert("1", unit("data_rate", "megabit_per_second"), unit("data_rate", "kilobyte_per_second")))
        assertExact("125", convert("1", unit("data_rate", "gigabit_per_second"), unit("data_rate", "megabyte_per_second")))
        assertExact("8", convert("1", unit("data_rate", "gigabyte_per_second"), unit("data_rate", "gigabit_per_second")))
    }

    @Test
    fun `every phase C3 category round trips through an independent pair`() {
        fun roundTrip(value: String, from: UnitDefinition, via: UnitDefinition) {
            val forward = convert(value, from, via)
            val back = engine.convert(forward, via, from)
            assertTrue(back is ConversionResult.Success)
            assertClose(value, (back as ConversionResult.Success).value, "0.000000001")
        }

        roundTrip("123.456", unit("mass", "kilogram"), unit("mass", "ounce"))
        roundTrip("123.456", unit("area", "square_meter"), unit("area", "acre"))
        roundTrip("123.456", unit("volume", "liter"), unit("volume", "us_cup"))
        roundTrip("123.456", unit("speed", "kilometer_per_hour"), unit("speed", "knot"))
        roundTrip("123.456", unit("time", "hour"), unit("time", "millisecond"))
        roundTrip("123.456", unit("data_storage", "gigabyte"), unit("data_storage", "gibibyte"))
        roundTrip("123.456", unit("data_rate", "megabit_per_second"), unit("data_rate", "kilobyte_per_second"))
    }

    @Test
    fun `zero succeeds and negative input fails for every phase C3 unit`() {
        val phaseC3CategoryIds = setOf(
            "mass",
            "area",
            "volume",
            "speed",
            "time",
            "data_storage",
            "data_rate"
        )
        catalog.categories
            .filter { it.id.value in phaseC3CategoryIds }
            .forEach { category ->
            val destination = category.defaultTo
            category.units.forEach { source ->
                assertExact("0", convert("0", source, destination))
                assertEquals(
                    ConversionResult.DomainError(DomainViolation.MUST_BE_NON_NEGATIVE),
                    engine.convert(BigDecimal("-1"), source, destination)
                )
            }
        }
    }

    @Test
    fun `incompatible existing and new categories are refused`() {
        assertEquals(
            ConversionResult.CategoryMismatch,
            engine.convert(BigDecimal.ONE, unit("length", "meter"), unit("mass", "kilogram"))
        )
        assertEquals(
            ConversionResult.CategoryMismatch,
            engine.convert(BigDecimal.ONE, unit("area", "square_meter"), unit("volume", "liter"))
        )
        assertEquals(
            ConversionResult.CategoryMismatch,
            engine.convert(
                BigDecimal.ONE,
                unit("data_storage", "byte"),
                unit("data_rate", "byte_per_second")
            )
        )
    }

    @Test
    fun `very small and very large supported magnitudes remain exact where defined`() {
        assertExact(
            "0.00000000000000000000000000000001",
            convert("1E-20", unit("mass", "microgram"), unit("mass", "metric_tonne"))
        )
        assertExact(
            "8796093022208000000000000",
            convert("1E12", unit("data_storage", "tebibyte"), unit("data_storage", "bit"))
        )
    }

    @Test
    fun `unsupported input and output magnitudes overflow explicitly`() {
        assertEquals(
            ConversionResult.Overflow,
            engine.convert(
                BigDecimal("1E10000"),
                unit("mass", "kilogram"),
                unit("mass", "gram")
            )
        )
        assertEquals(
            ConversionResult.Overflow,
            engine.convert(
                BigDecimal("1E-9990"),
                unit("data_storage", "bit"),
                unit("data_storage", "tebibyte")
            )
        )
    }
}
