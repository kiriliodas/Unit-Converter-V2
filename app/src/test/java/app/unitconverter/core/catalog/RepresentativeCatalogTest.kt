package app.unitconverter.core.catalog

import app.unitconverter.core.model.CategoryId
import app.unitconverter.core.model.ConversionDefinition
import app.unitconverter.core.model.DomainRule
import app.unitconverter.core.model.FuelRole
import app.unitconverter.core.model.UnitDefinition
import app.unitconverter.core.model.UnitId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Catalog integrity. The original Phase C1 categories remain first and
 * unchanged; the Phase C3 and Phase C5 categories are appended after
 * them. These checks protect the stable identifiers that persistence
 * relies on.
 */
class RepresentativeCatalogTest {

    private val catalog = RepresentativeCatalog.catalog

    @Test
    fun `catalog preserves categories through C3 and appends phase C5`() {
        assertEquals(17, catalog.categories.size)
        assertEquals(
            listOf(
                "length",
                "temperature",
                "fuel_economy",
                "mass",
                "area",
                "volume",
                "speed",
                "time",
                "data_storage",
                "data_rate",
                "pressure",
                "energy",
                "power",
                "force",
                "frequency",
                "angle",
                "torque"
            ),
            catalog.categories.map { it.id.value }
        )
        assertEquals(13, catalog.requireCategory(CategoryId("length")).units.size)
        assertEquals(4, catalog.requireCategory(CategoryId("temperature")).units.size)
        assertEquals(4, catalog.requireCategory(CategoryId("fuel_economy")).units.size)
        assertEquals(7, catalog.requireCategory(CategoryId("mass")).units.size)
        assertEquals(7, catalog.requireCategory(CategoryId("area")).units.size)
        assertEquals(9, catalog.requireCategory(CategoryId("volume")).units.size)
        assertEquals(5, catalog.requireCategory(CategoryId("speed")).units.size)
        assertEquals(6, catalog.requireCategory(CategoryId("time")).units.size)
        assertEquals(10, catalog.requireCategory(CategoryId("data_storage")).units.size)
        assertEquals(8, catalog.requireCategory(CategoryId("data_rate")).units.size)
        assertEquals(8, catalog.requireCategory(CategoryId("pressure")).units.size)
        assertEquals(7, catalog.requireCategory(CategoryId("energy")).units.size)
        assertEquals(5, catalog.requireCategory(CategoryId("power")).units.size)
        assertEquals(4, catalog.requireCategory(CategoryId("force")).units.size)
        assertEquals(5, catalog.requireCategory(CategoryId("frequency")).units.size)
        assertEquals(4, catalog.requireCategory(CategoryId("angle")).units.size)
        assertEquals(4, catalog.requireCategory(CategoryId("torque")).units.size)
    }

    @Test
    fun `every unit is uniquely addressable and fully described`() {
        val keys = catalog.allUnits.map { it.categoryId to it.id }
        assertEquals(keys.size, keys.distinct().size)
        catalog.allUnits.forEach { unit ->
            assertTrue(unit.symbol.isNotBlank())
            assertNotEquals(0, unit.nameRes)
            assertEquals(unit, catalog.requireUnit(unit.categoryId, unit.id))
        }
    }

    @Test
    fun `default pairs are two different units in the same category`() {
        catalog.categories.forEach { category ->
            assertNotEquals(category.defaultFrom.id, category.defaultTo.id)
            assertTrue(category.units.contains(category.defaultFrom))
            assertTrue(category.units.contains(category.defaultTo))
        }
    }

    @Test
    fun `all linear factors are strictly positive`() {
        catalog.allUnits
            .mapNotNull { it.conversion as? ConversionDefinition.Linear }
            .forEach { assertTrue(it.factorToBase.signum() > 0) }
    }

    @Test
    fun `fuel units cannot opt out of the positive domain`() {
        assertThrows(IllegalArgumentException::class.java) {
            UnitDefinition(
                id = UnitId("unsafe_fuel"),
                categoryId = CategoryId("fuel_economy"),
                nameRes = 0,
                symbol = "unsafe",
                conversion = ConversionDefinition.Fuel(FuelRole.LITERS_PER_100_KM),
                domain = DomainRule.AnyReal
            )
        }
    }

    @Test
    fun `capitalized or punctuated ids are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { CategoryId("Length") }
        assertThrows(IllegalArgumentException::class.java) { UnitId("light-year") }
        assertThrows(IllegalArgumentException::class.java) { UnitId("") }
    }
}
