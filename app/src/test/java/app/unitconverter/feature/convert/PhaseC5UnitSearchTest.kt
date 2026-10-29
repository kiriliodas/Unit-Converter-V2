package app.unitconverter.feature.convert

import app.unitconverter.core.catalog.RepresentativeCatalog
import app.unitconverter.core.model.CategoryId
import app.unitconverter.core.model.UnitDefinition
import app.unitconverter.core.model.UnitId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseC5UnitSearchTest {

    private val catalog = RepresentativeCatalog.catalog

    private fun unit(category: String, unit: String): UnitDefinition =
        catalog.requireUnit(CategoryId(category), UnitId(unit))

    private fun rank(query: String, name: String, unit: UnitDefinition): Int =
        UnitSearch.rank(query, name, unit.symbol, unit.aliases)

    @Test
    fun exactScientificSymbolsRankHighestAndStayDistinct() {
        assertEquals(4, rank("Pa", "Pascal", unit("pressure", "pascal")))
        assertEquals(
            4,
            rank(
                "psi",
                "Pound-force per square inch",
                unit("pressure", "pound_per_square_inch")
            )
        )
        assertEquals(
            4,
            rank(
                "mmHg",
                "Millimeter of mercury",
                unit("pressure", "millimeter_of_mercury")
            )
        )
        assertEquals(4, rank("eV", "Electronvolt", unit("energy", "electronvolt")))
        assertEquals(4, rank("N·m", "Newton meter", unit("torque", "newton_meter")))
        assertEquals(4, rank("°", "Degree", unit("angle", "degree")))
        assertEquals(
            -1,
            rank("psi", "Pascal", unit("pressure", "pascal"))
        )
    }

    @Test
    fun horsepowerConventionsHaveDistinctExactSymbols() {
        val mechanical = unit("power", "mechanical_horsepower")
        val metric = unit("power", "metric_horsepower")

        assertEquals(4, rank("hp", "Mechanical horsepower", mechanical))
        assertEquals(4, rank("PS", "Metric horsepower", metric))
        assertEquals(2, rank("ch", "Metric horsepower", metric))
        assertTrue(rank("hp", "Metric horsepower", metric) < 4)
        assertTrue(rank("PS", "Mechanical horsepower", mechanical) < 4)
    }

    @Test
    fun frenchAliasesFindScientificUnitsWithoutAccents() {
        assertEquals(
            2,
            rank(
                "millimetre de mercure",
                "Millimeter of mercury",
                unit("pressure", "millimeter_of_mercury")
            )
        )
        assertEquals(
            2,
            rank(
                "cheval-vapeur mecanique",
                "Mechanical horsepower",
                unit("power", "mechanical_horsepower")
            )
        )
        assertEquals(
            2,
            rank(
                "tour par minute",
                "Revolution per minute",
                unit("frequency", "revolution_per_minute")
            )
        )
        assertEquals(
            2,
            rank(
                "tr/min",
                "Revolution per minute",
                unit("frequency", "revolution_per_minute")
            )
        )
        assertEquals(
            2,
            rank(
                "livre-force pied",
                "Pound-force foot",
                unit("torque", "pound_force_foot")
            )
        )
    }

    @Test
    fun newtonAndNewtonMeterSearchesDoNotConflateForceTorqueOrEnergy() {
        assertEquals(4, rank("N", "Newton", unit("force", "newton")))
        assertEquals(
            4,
            rank("N·m", "Newton meter", unit("torque", "newton_meter"))
        )
        assertEquals(
            -1,
            rank("N·m", "Joule", unit("energy", "joule"))
        )
        assertEquals(
            -1,
            rank("J", "Newton meter", unit("torque", "newton_meter"))
        )
    }
}
