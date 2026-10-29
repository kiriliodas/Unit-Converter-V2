package app.unitconverter.core.catalog

import app.unitconverter.core.model.CategoryGroup
import app.unitconverter.core.model.CategoryId
import app.unitconverter.core.model.ConversionDefinition
import app.unitconverter.core.model.DomainRule
import app.unitconverter.core.model.UnitId
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseC5CatalogTest {

    private val catalog = RepresentativeCatalog.catalog

    private fun unitIds(categoryId: String): List<String> =
        catalog.requireCategory(CategoryId(categoryId)).units.map { it.id.value }

    private fun symbol(categoryId: String, unitId: String): String =
        catalog.requireUnit(CategoryId(categoryId), UnitId(unitId)).symbol

    @Test
    fun `phase C5 categories expose exactly the planned unit ids`() {
        assertEquals(
            listOf(
                "pascal", "kilopascal", "megapascal", "bar", "millibar",
                "standard_atmosphere", "pound_per_square_inch",
                "millimeter_of_mercury"
            ),
            unitIds("pressure")
        )
        assertEquals(
            listOf(
                "joule", "kilojoule", "calorie", "kilocalorie", "watt_hour",
                "kilowatt_hour", "electronvolt"
            ),
            unitIds("energy")
        )
        assertEquals(
            listOf(
                "watt", "kilowatt", "megawatt", "mechanical_horsepower",
                "metric_horsepower"
            ),
            unitIds("power")
        )
        assertEquals(
            listOf("newton", "kilonewton", "pound_force", "dyne"),
            unitIds("force")
        )
        assertEquals(
            listOf(
                "hertz", "kilohertz", "megahertz", "gigahertz",
                "revolution_per_minute"
            ),
            unitIds("frequency")
        )
        assertEquals(
            listOf("degree", "radian", "gradian", "turn"),
            unitIds("angle")
        )
        assertEquals(
            listOf(
                "newton_meter", "kilonewton_meter", "pound_force_foot",
                "pound_force_inch"
            ),
            unitIds("torque")
        )
    }

    @Test
    fun `scientific categories are appended after all C3 categories`() {
        assertEquals(
            listOf(
                "pressure", "energy", "power", "force", "frequency",
                "angle", "torque"
            ),
            catalog.categories.takeLast(7).map { it.id.value }
        )
        assertEquals(
            listOf(
                "length", "temperature", "fuel_economy", "mass", "area",
                "volume", "speed", "time", "data_storage", "data_rate"
            ),
            catalog.categories.take(10).map { it.id.value }
        )
        catalog.categories.takeLast(7).forEach { category ->
            assertEquals(CategoryGroup.SCIENCE_ENGINEERING, category.group)
        }
    }

    @Test
    fun `scientific defaults domains and canonical units are explicit`() {
        val expected = mapOf(
            "pressure" to Triple("kilopascal", "pound_per_square_inch", "pascal"),
            "energy" to Triple("kilojoule", "kilocalorie", "joule"),
            "power" to Triple("kilowatt", "mechanical_horsepower", "watt"),
            "force" to Triple("newton", "pound_force", "newton"),
            "frequency" to Triple("hertz", "revolution_per_minute", "hertz"),
            "angle" to Triple("degree", "radian", "degree"),
            "torque" to Triple("newton_meter", "pound_force_foot", "newton_meter")
        )
        val signedCategories = setOf("force", "angle", "torque")

        expected.forEach { (categoryId, values) ->
            val category = catalog.requireCategory(CategoryId(categoryId))
            assertEquals(values.first, category.defaultFromId.value)
            assertEquals(values.second, category.defaultToId.value)

            val base = category.requireUnit(UnitId(values.third))
            val baseFactor = (base.conversion as ConversionDefinition.Linear).factorToBase
            assertEquals(0, BigDecimal.ONE.compareTo(baseFactor))

            category.units.forEach { unit ->
                assertTrue(unit.conversion is ConversionDefinition.Linear)
                assertEquals(
                    if (categoryId in signedCategories) {
                        DomainRule.AnyReal
                    } else {
                        DomainRule.NonNegative
                    },
                    unit.domain
                )
                assertTrue(unit.aliases.isNotEmpty())
            }
        }
    }

    @Test
    fun `scientific symbols remain distinct and internationally recognizable`() {
        assertEquals("Pa", symbol("pressure", "pascal"))
        assertEquals("psi", symbol("pressure", "pound_per_square_inch"))
        assertEquals("mmHg", symbol("pressure", "millimeter_of_mercury"))
        assertEquals("J", symbol("energy", "joule"))
        assertEquals("eV", symbol("energy", "electronvolt"))
        assertEquals("hp", symbol("power", "mechanical_horsepower"))
        assertEquals("PS", symbol("power", "metric_horsepower"))
        assertEquals("lbf", symbol("force", "pound_force"))
        assertEquals("dyn", symbol("force", "dyne"))
        assertEquals("Hz", symbol("frequency", "hertz"))
        assertEquals("rpm", symbol("frequency", "revolution_per_minute"))
        assertEquals("°", symbol("angle", "degree"))
        assertEquals("rad", symbol("angle", "radian"))
        assertEquals("gon", symbol("angle", "gradian"))
        assertEquals("N·m", symbol("torque", "newton_meter"))
        assertEquals("lbf·ft", symbol("torque", "pound_force_foot"))
        assertEquals("lbf·in", symbol("torque", "pound_force_inch"))
    }

    @Test
    fun `energy and torque are distinct catalog categories`() {
        val energy = catalog.requireCategory(CategoryId("energy"))
        val torque = catalog.requireCategory(CategoryId("torque"))

        assertNotEquals(energy.id, torque.id)
        assertTrue(energy.unit(UnitId("joule")) != null)
        assertTrue(energy.unit(UnitId("newton_meter")) == null)
        assertTrue(torque.unit(UnitId("newton_meter")) != null)
        assertTrue(torque.unit(UnitId("joule")) == null)
    }
}
