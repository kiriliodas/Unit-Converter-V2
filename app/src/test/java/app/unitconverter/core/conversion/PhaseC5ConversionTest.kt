package app.unitconverter.core.conversion

import app.unitconverter.core.catalog.RepresentativeCatalog
import app.unitconverter.core.model.CategoryId
import app.unitconverter.core.model.UnitDefinition
import app.unitconverter.core.model.UnitId
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseC5ConversionTest {

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
        assertEquals(
            "expected $expected but was $actual",
            0,
            BigDecimal(expected).compareTo(actual)
        )
    }

    private fun assertClose(expected: String, actual: BigDecimal, tolerance: String) {
        val difference = (BigDecimal(expected) - actual).abs()
        assertTrue(
            "expected $actual within $tolerance of $expected (diff $difference)",
            difference <= BigDecimal(tolerance)
        )
    }

    @Test
    fun `pressure uses verified pascal bar atmosphere psi and mmHg definitions`() {
        assertExact("100000", convert("1", unit("pressure", "bar"), unit("pressure", "pascal")))
        assertExact("100", convert("1", unit("pressure", "millibar"), unit("pressure", "pascal")))
        assertExact(
            "101.325",
            convert("1", unit("pressure", "standard_atmosphere"), unit("pressure", "kilopascal"))
        )
        assertExact("10", convert("1", unit("pressure", "megapascal"), unit("pressure", "bar")))
        assertClose(
            "6894.7572931683613367",
            convert("1", unit("pressure", "pound_per_square_inch"), unit("pressure", "pascal")),
            "0.000000000001"
        )
        assertExact(
            "133.322387415",
            convert("1", unit("pressure", "millimeter_of_mercury"), unit("pressure", "pascal"))
        )
        assertClose(
            "14.6959487755134",
            convert(
                "1",
                unit("pressure", "standard_atmosphere"),
                unit("pressure", "pound_per_square_inch")
            ),
            "0.000000000001"
        )
    }

    @Test
    fun `energy uses thermochemical calories watt hours and exact electronvolt`() {
        assertExact("4.184", convert("1", unit("energy", "calorie"), unit("energy", "joule")))
        assertExact(
            "4.184",
            convert("1", unit("energy", "kilocalorie"), unit("energy", "kilojoule"))
        )
        assertExact("3600", convert("1", unit("energy", "watt_hour"), unit("energy", "joule")))
        assertExact(
            "3600000",
            convert("1", unit("energy", "kilowatt_hour"), unit("energy", "joule"))
        )
        assertExact(
            "0.0000000000000000001602176634",
            convert("1", unit("energy", "electronvolt"), unit("energy", "joule"))
        )
        assertClose(
            "6241509074460762607.776",
            convert("1", unit("energy", "joule"), unit("energy", "electronvolt")),
            "1000"
        )
    }

    @Test
    fun `power distinguishes mechanical and metric horsepower`() {
        assertExact(
            "745.69987158227022",
            convert("1", unit("power", "mechanical_horsepower"), unit("power", "watt"))
        )
        assertExact(
            "735.49875",
            convert("1", unit("power", "metric_horsepower"), unit("power", "watt"))
        )
        assertClose(
            "1.3410220895950279",
            convert("1", unit("power", "kilowatt"), unit("power", "mechanical_horsepower")),
            "0.000000000000001"
        )
        assertClose(
            "1.3596216173039043",
            convert("1", unit("power", "kilowatt"), unit("power", "metric_horsepower")),
            "0.000000000000001"
        )
        assertExact("1000", convert("1", unit("power", "megawatt"), unit("power", "kilowatt")))
    }

    @Test
    fun `force uses exact pound force and dyne and accepts directed negatives`() {
        assertExact(
            "4.4482216152605",
            convert("1", unit("force", "pound_force"), unit("force", "newton"))
        )
        assertExact("0.00001", convert("1", unit("force", "dyne"), unit("force", "newton")))
        assertExact("1000", convert("1", unit("force", "kilonewton"), unit("force", "newton")))
        assertExact(
            "-8.896443230521",
            convert("-2", unit("force", "pound_force"), unit("force", "newton"))
        )
    }

    @Test
    fun `frequency treats rpm as revolutions per minute in hertz`() {
        assertClose(
            "1",
            convert(
                "60",
                unit("frequency", "revolution_per_minute"),
                unit("frequency", "hertz")
            ),
            "0.000000000000000000000000001"
        )
        assertClose(
            "60",
            convert(
                "1",
                unit("frequency", "hertz"),
                unit("frequency", "revolution_per_minute")
            ),
            "0.000000000000000000000001"
        )
        assertClose(
            "0.0166666666666666666667",
            convert(
                "1",
                unit("frequency", "revolution_per_minute"),
                unit("frequency", "hertz")
            ),
            "0.000000000000000000001"
        )
        assertExact(
            "1000",
            convert("1", unit("frequency", "gigahertz"), unit("frequency", "megahertz"))
        )
    }

    @Test
    fun `angle uses exact turns and gradians and pi-based radians`() {
        assertExact("360", convert("1", unit("angle", "turn"), unit("angle", "degree")))
        assertExact("180", convert("200", unit("angle", "gradian"), unit("angle", "degree")))
        assertExact("400", convert("1", unit("angle", "turn"), unit("angle", "gradian")))
        assertClose(
            "3.141592653589793",
            convert("180", unit("angle", "degree"), unit("angle", "radian")),
            "0.000000000000001"
        )
        assertClose(
            "6.283185307179586",
            convert("1", unit("angle", "turn"), unit("angle", "radian")),
            "0.000000000000001"
        )
        assertClose(
            "-1.5707963267948966",
            convert("-90", unit("angle", "degree"), unit("angle", "radian")),
            "0.000000000000001"
        )
    }

    @Test
    fun `torque uses exact pound force foot factors and accepts direction`() {
        assertExact(
            "1.3558179483314004",
            convert("1", unit("torque", "pound_force_foot"), unit("torque", "newton_meter"))
        )
        assertExact(
            "0.1129848290276167",
            convert("1", unit("torque", "pound_force_inch"), unit("torque", "newton_meter"))
        )
        assertExact(
            "12",
            convert("1", unit("torque", "pound_force_foot"), unit("torque", "pound_force_inch"))
        )
        assertClose(
            "-7.3756214927726536",
            convert("-10", unit("torque", "newton_meter"), unit("torque", "pound_force_foot")),
            "0.000000000000001"
        )
    }

    @Test
    fun `every scientific category round trips through an independent pair`() {
        fun roundTrip(value: String, from: UnitDefinition, via: UnitDefinition) {
            val forward = convert(value, from, via)
            val back = engine.convert(forward, via, from)
            assertTrue(back is ConversionResult.Success)
            assertClose(value, (back as ConversionResult.Success).value, "0.000000001")
        }

        roundTrip("123.456", unit("pressure", "kilopascal"), unit("pressure", "pound_per_square_inch"))
        roundTrip("123.456", unit("energy", "kilojoule"), unit("energy", "electronvolt"))
        roundTrip("123.456", unit("power", "kilowatt"), unit("power", "metric_horsepower"))
        roundTrip("-123.456", unit("force", "newton"), unit("force", "dyne"))
        roundTrip("123.456", unit("frequency", "hertz"), unit("frequency", "revolution_per_minute"))
        roundTrip("-123.456", unit("angle", "degree"), unit("angle", "radian"))
        roundTrip("-123.456", unit("torque", "newton_meter"), unit("torque", "pound_force_inch"))
    }

    @Test
    fun `scientific domains accept zero and reject only unsigned negatives`() {
        listOf("pressure", "energy", "power", "frequency").forEach { categoryId ->
            val category = catalog.requireCategory(CategoryId(categoryId))
            category.units.forEach { source ->
                assertExact("0", convert("0", source, category.defaultTo))
                assertEquals(
                    ConversionResult.DomainError(DomainViolation.MUST_BE_NON_NEGATIVE),
                    engine.convert(BigDecimal("-1"), source, category.defaultTo)
                )
            }
        }

        listOf("force", "angle", "torque").forEach { categoryId ->
            val category = catalog.requireCategory(CategoryId(categoryId))
            category.units.forEach { source ->
                assertExact("0", convert("0", source, category.defaultTo))
                assertTrue(
                    engine.convert(BigDecimal("-1"), source, category.defaultTo)
                        is ConversionResult.Success
                )
            }
        }
    }

    @Test
    fun `joule and Newton meter are never silently interconverted`() {
        assertEquals(
            ConversionResult.CategoryMismatch,
            engine.convert(
                BigDecimal.ONE,
                unit("energy", "joule"),
                unit("torque", "newton_meter")
            )
        )
        assertEquals(
            ConversionResult.CategoryMismatch,
            engine.convert(
                BigDecimal.ONE,
                unit("torque", "newton_meter"),
                unit("energy", "joule")
            )
        )
    }

    @Test
    fun `scientific extremes remain finite inside supported magnitude limits`() {
        assertExact(
            "0.000000000000000000000000000000000000001602176634",
            convert("1E-20", unit("energy", "electronvolt"), unit("energy", "joule"))
        )
        assertExact(
            "1000000000000000000000",
            convert("1E12", unit("frequency", "gigahertz"), unit("frequency", "hertz"))
        )
        assertEquals(
            ConversionResult.Overflow,
            engine.convert(
                BigDecimal("1E10000"),
                unit("pressure", "megapascal"),
                unit("pressure", "pascal")
            )
        )
    }
}
