package app.unitconverter.core.catalog

import app.unitconverter.core.model.CategoryGroup
import app.unitconverter.core.model.CategoryId
import app.unitconverter.core.model.ConversionDefinition
import app.unitconverter.core.model.DomainRule
import app.unitconverter.core.model.UnitId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseC3CatalogTest {

    private val catalog = RepresentativeCatalog.catalog

    private fun unitIds(categoryId: String): List<String> =
        catalog.requireCategory(CategoryId(categoryId)).units.map { it.id.value }

    @Test
    fun `phase C3 categories expose exactly the requested unit ids`() {
        assertEquals(
            listOf(
                "microgram", "milligram", "gram", "kilogram", "metric_tonne",
                "ounce", "pound"
            ),
            unitIds("mass")
        )
        assertEquals(
            listOf(
                "square_millimeter", "square_centimeter", "square_meter",
                "hectare", "square_kilometer", "square_foot", "acre"
            ),
            unitIds("area")
        )
        assertEquals(
            listOf(
                "milliliter", "centiliter", "liter", "cubic_meter",
                "us_teaspoon", "us_tablespoon", "us_cup", "us_gallon",
                "imperial_gallon"
            ),
            unitIds("volume")
        )
        assertEquals(
            listOf(
                "meter_per_second", "kilometer_per_hour", "mile_per_hour",
                "foot_per_second", "knot"
            ),
            unitIds("speed")
        )
        assertEquals(
            listOf("millisecond", "second", "minute", "hour", "day", "week"),
            unitIds("time")
        )
        assertEquals(
            listOf(
                "bit", "byte", "kilobyte", "megabyte", "gigabyte", "terabyte",
                "kibibyte", "mebibyte", "gibibyte", "tebibyte"
            ),
            unitIds("data_storage")
        )
        assertEquals(
            listOf(
                "bit_per_second", "kilobit_per_second", "megabit_per_second",
                "gigabit_per_second", "byte_per_second", "kilobyte_per_second",
                "megabyte_per_second", "gigabyte_per_second"
            ),
            unitIds("data_rate")
        )
    }

    @Test
    fun `original three categories retain every unit id`() {
        assertEquals(
            listOf(
                "nanometer", "micrometer", "millimeter", "centimeter", "meter",
                "kilometer", "inch", "foot", "yard", "mile", "nautical_mile",
                "astronomical_unit", "light_year"
            ),
            unitIds("length")
        )
        assertEquals(
            listOf("celsius", "fahrenheit", "kelvin", "rankine"),
            unitIds("temperature")
        )
        assertEquals(
            listOf(
                "liters_per_100_kilometers", "kilometers_per_liter",
                "miles_per_us_gallon", "miles_per_imperial_gallon"
            ),
            unitIds("fuel_economy")
        )
    }

    @Test
    fun `new categories are linear non-negative and carry useful defaults`() {
        val expectedDefaults = mapOf(
            "mass" to ("kilogram" to "pound"),
            "area" to ("square_meter" to "square_foot"),
            "volume" to ("liter" to "us_gallon"),
            "speed" to ("kilometer_per_hour" to "mile_per_hour"),
            "time" to ("hour" to "minute"),
            "data_storage" to ("megabyte" to "mebibyte"),
            "data_rate" to ("megabit_per_second" to "megabyte_per_second")
        )

        expectedDefaults.forEach { (categoryId, defaults) ->
            val category = catalog.requireCategory(CategoryId(categoryId))
            assertEquals(defaults.first, category.defaultFromId.value)
            assertEquals(defaults.second, category.defaultToId.value)
            category.units.forEach { unit ->
                assertTrue(unit.conversion is ConversionDefinition.Linear)
                assertEquals(DomainRule.NonNegative, unit.domain)
                assertTrue(unit.aliases.isNotEmpty())
            }
        }
    }

    @Test
    fun `computing categories and everyday categories are grouped distinctly`() {
        listOf("mass", "area", "volume", "speed", "time").forEach { categoryId ->
            assertEquals(
                CategoryGroup.EVERYDAY,
                catalog.requireCategory(CategoryId(categoryId)).group
            )
        }
        listOf("data_storage", "data_rate").forEach { categoryId ->
            assertEquals(
                CategoryGroup.COMPUTING,
                catalog.requireCategory(CategoryId(categoryId)).group
            )
        }
    }

    @Test
    fun `localized resource ids and bit byte symbols stay unambiguous`() {
        val allNameResources = catalog.categories.map { it.nameRes } +
            catalog.allUnits.map { it.nameRes }
        assertEquals(allNameResources.size, allNameResources.distinct().size)
        assertTrue(allNameResources.all { it != 0 })

        assertEquals(
            "bit",
            catalog.requireUnit(CategoryId("data_storage"), UnitId("bit")).symbol
        )
        assertEquals(
            "B",
            catalog.requireUnit(CategoryId("data_storage"), UnitId("byte")).symbol
        )
        assertEquals(
            "Mbit/s",
            catalog.requireUnit(CategoryId("data_rate"), UnitId("megabit_per_second")).symbol
        )
        assertEquals(
            "MB/s",
            catalog.requireUnit(CategoryId("data_rate"), UnitId("megabyte_per_second")).symbol
        )
    }

    @Test
    fun `variable calendar durations are not represented as fixed time units`() {
        val time = catalog.requireCategory(CategoryId("time"))
        assertNull(time.unit(UnitId("month")))
        assertNull(time.unit(UnitId("year")))
    }
}
