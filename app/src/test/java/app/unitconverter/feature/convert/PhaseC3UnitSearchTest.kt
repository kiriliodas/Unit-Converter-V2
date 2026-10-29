package app.unitconverter.feature.convert

import app.unitconverter.core.catalog.RepresentativeCatalog
import app.unitconverter.core.model.CategoryId
import app.unitconverter.core.model.UnitDefinition
import app.unitconverter.core.model.UnitId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseC3UnitSearchTest {

    private val catalog = RepresentativeCatalog.catalog

    private fun unit(category: String, unit: String): UnitDefinition =
        catalog.requireUnit(CategoryId(category), UnitId(unit))

    @Test
    fun everyUnitIsRankedFirstByItsExactSymbolWithinItsOwnDefinition() {
        catalog.allUnits.forEach { unit ->
            assertEquals(
                "symbol ${unit.symbol} for ${unit.categoryId.value}/${unit.id.value}",
                4,
                UnitSearch.rank(unit.symbol, "", unit.symbol, unit.aliases)
            )
        }
    }

    @Test
    fun frenchSearchTermsFindNewUnitsWhenEnglishNameIsDisplayed() {
        assertEquals(
            2,
            UnitSearch.rank(
                "kilogramme",
                "Kilogram",
                unit("mass", "kilogram").symbol,
                unit("mass", "kilogram").aliases
            )
        )
        assertEquals(
            2,
            UnitSearch.rank(
                "kibioctet",
                "Kibibyte",
                unit("data_storage", "kibibyte").symbol,
                unit("data_storage", "kibibyte").aliases
            )
        )
        assertEquals(
            2,
            UnitSearch.rank(
                "mégaoctet par seconde",
                "Megabyte per second",
                unit("data_rate", "megabyte_per_second").symbol,
                unit("data_rate", "megabyte_per_second").aliases
            )
        )
        assertEquals(
            2,
            UnitSearch.rank(
                "nœud",
                "Knot",
                unit("speed", "knot").symbol,
                unit("speed", "knot").aliases
            )
        )
    }

    @Test
    fun unaccentedFrenchNamesAndAliasesFindFrenchUnits() {
        assertEquals(
            3,
            UnitSearch.rank(
                "cuillere a cafe",
                "Cuillère à café (US)",
                unit("volume", "us_teaspoon").symbol,
                unit("volume", "us_teaspoon").aliases
            )
        )
        assertEquals(
            2,
            UnitSearch.rank(
                "ft2",
                "Square foot",
                unit("area", "square_foot").symbol,
                unit("area", "square_foot").aliases
            )
        )
        assertTrue(
            UnitSearch.matches(
                "tasse",
                "Tasse US",
                unit("volume", "us_cup").symbol,
                unit("volume", "us_cup").aliases
            )
        )
    }

    @Test
    fun englishNameAndSymbolSearchFindImperialAndComputingUnits() {
        assertEquals(
            3,
            UnitSearch.rank(
                "square foot",
                "Square foot",
                unit("area", "square_foot").symbol,
                unit("area", "square_foot").aliases
            )
        )
        assertEquals(
            4,
            UnitSearch.rank(
                "mph",
                "Mile per hour",
                unit("speed", "mile_per_hour").symbol,
                unit("speed", "mile_per_hour").aliases
            )
        )
        assertEquals(
            4,
            UnitSearch.rank(
                "TiB",
                "Tebibyte",
                unit("data_storage", "tebibyte").symbol,
                unit("data_storage", "tebibyte").aliases
            )
        )
    }

    @Test
    fun exactByteSymbolOutranksABitSubstringMatch() {
        val bit = unit("data_storage", "bit")
        val byte = unit("data_storage", "byte")

        val byteRank = UnitSearch.rank("B", "Byte", byte.symbol, byte.aliases)
        val bitRank = UnitSearch.rank("B", "Bit", bit.symbol, bit.aliases)

        assertEquals(4, byteRank)
        assertEquals(1, bitRank)
        assertTrue(byteRank > bitRank)
        assertEquals(
            -1,
            UnitSearch.rank("bit", "Byte", byte.symbol, byte.aliases)
        )
    }

    @Test
    fun bitAndByteDataRatesHaveDistinctSearchableSymbols() {
        val megabit = unit("data_rate", "megabit_per_second")
        val megabyte = unit("data_rate", "megabyte_per_second")

        assertEquals(
            4,
            UnitSearch.rank("Mbit/s", "Megabit per second", megabit.symbol, megabit.aliases)
        )
        assertEquals(
            4,
            UnitSearch.rank("MB/s", "Megabyte per second", megabyte.symbol, megabyte.aliases)
        )
        assertEquals(
            -1,
            UnitSearch.rank("Mbit/s", "Megabyte per second", megabyte.symbol, megabyte.aliases)
        )
    }
}
