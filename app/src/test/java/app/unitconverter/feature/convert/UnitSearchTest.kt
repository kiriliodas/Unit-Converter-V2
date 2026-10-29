package app.unitconverter.feature.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitSearchTest {

    @Test
    fun normalizeStripsAccentsCaseAndSpaces() {
        assertEquals("kilometre", UnitSearch.normalize(" Kilomètre "))
        assertEquals("celsius", UnitSearch.normalize("CELSIUS"))
    }

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(UnitSearch.matches("", "Kilometer", "km", emptySet()))
        assertEquals(0, UnitSearch.rank("  ", "Kilometer", "km", emptySet()))
    }

    @Test
    fun exactSymbolRanksHighest() {
        assertEquals(4, UnitSearch.rank("km", "Kilometer", "km", emptySet()))
        assertEquals(4, UnitSearch.rank("KM", "Kilometer", "km", emptySet()))
    }

    @Test
    fun namePrefixMatchesAccentInsensitively() {
        assertEquals(3, UnitSearch.rank("kilometre", "Kilomètre", "km", emptySet()))
        assertEquals(3, UnitSearch.rank("kilo", "Kilometer", "km", emptySet()))
    }

    @Test
    fun aliasPrefixMatches() {
        assertEquals(
            2,
            UnitSearch.rank("micron", "Micrometer", "µm", setOf("micrometre", "micron"))
        )
    }

    @Test
    fun substringMatchesNameAndSymbol() {
        assertEquals(1, UnitSearch.rank("meter", "Kilometer", "km", emptySet()))
        assertTrue(UnitSearch.matches("100 km", "Liters per 100 kilometers", "L/100 km", emptySet()))
    }

    @Test
    fun unrelatedQueryDoesNotMatch() {
        assertEquals(-1, UnitSearch.rank("parsec", "Kilometer", "km", setOf("kilometre")))
        assertFalse(UnitSearch.matches("parsec", "Kilometer", "km", setOf("kilometre")))
    }
}
