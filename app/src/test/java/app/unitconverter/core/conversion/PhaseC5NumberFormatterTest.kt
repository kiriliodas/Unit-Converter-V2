package app.unitconverter.core.conversion

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PhaseC5NumberFormatterTest {

    private val formatter = NumberFormatter()

    private fun options(precision: PrecisionMode) = NumberFormatOptions(
        precision = precision,
        useGrouping = true,
        decimalSeparator = '.',
        groupingSeparator = ','
    )

    @Test
    fun `scientific mode keeps exact mantissa places and renormalizes`() {
        assertEquals(
            "1.23E+4",
            formatter.format(BigDecimal("12345"), options(PrecisionMode.Scientific(2)))
        )
        assertEquals(
            "1.200E+0",
            formatter.format(BigDecimal("1.2"), options(PrecisionMode.Scientific(3)))
        )
        assertEquals(
            "1.00E+4",
            formatter.format(BigDecimal("9999"), options(PrecisionMode.Scientific(2)))
        )
        assertEquals(
            "1E+1",
            formatter.format(BigDecimal("12.3"), options(PrecisionMode.Scientific(0)))
        )
    }

    @Test
    fun `scientific mode handles tiny negative and signed zero values`() {
        assertEquals(
            "-1.000E-10",
            formatter.format(
                BigDecimal("-0.0000000001"),
                options(PrecisionMode.Scientific(3))
            )
        )
        assertEquals(
            "0.00E+0",
            formatter.format(BigDecimal("-0.000"), options(PrecisionMode.Scientific(2)))
        )
        assertEquals(
            "0E+0",
            formatter.format(BigDecimal("-0"), options(PrecisionMode.Scientific(0)))
        )
        assertEquals(
            "0.00",
            formatter.format(BigDecimal("-0.004"), options(PrecisionMode.Fixed(2)))
        )
    }

    @Test
    fun `half up ties and fixed boundaries are deterministic for both signs`() {
        assertEquals("3", formatter.format(BigDecimal("2.5"), options(PrecisionMode.Fixed(0))))
        assertEquals("-3", formatter.format(BigDecimal("-2.5"), options(PrecisionMode.Fixed(0))))
        assertEquals(
            "1.01",
            formatter.format(BigDecimal("1.005"), options(PrecisionMode.Fixed(2)))
        )
        assertEquals(
            "-1.01",
            formatter.format(BigDecimal("-1.005"), options(PrecisionMode.Fixed(2)))
        )
        assertEquals(
            "0.000000000001",
            formatter.format(
                BigDecimal("0.000000000001"),
                options(PrecisionMode.Fixed(12))
            )
        )
        assertEquals(
            "0.000000000001",
            formatter.format(
                BigDecimal("0.0000000000005"),
                options(PrecisionMode.Fixed(12))
            )
        )
    }

    @Test
    fun `smart plain and scientific thresholds stay readable at the edges`() {
        assertEquals(
            "0.000001",
            formatter.format(BigDecimal("0.000001"), options(PrecisionMode.Smart()))
        )
        assertEquals(
            "1E-7",
            formatter.format(BigDecimal("0.0000001"), options(PrecisionMode.Smart()))
        )
        assertEquals(
            "1E+15",
            formatter.format(
                BigDecimal("999999999999999"),
                options(PrecisionMode.Smart())
            )
        )
        assertEquals(
            "-1E-10",
            formatter.format(
                BigDecimal("-0.0000000001"),
                options(PrecisionMode.Smart())
            )
        )
    }

    @Test
    fun `formatting never mutates the internal BigDecimal`() {
        val original = BigDecimal("1.2300")
        val originalText = original.toPlainString()
        val originalScale = original.scale()

        assertEquals(
            "1.23E+0",
            formatter.format(original, options(PrecisionMode.Scientific(2)))
        )
        assertEquals(originalText, original.toPlainString())
        assertEquals(originalScale, original.scale())
    }

    @Test
    fun `scientific mode rejects decimal places outside zero to twelve`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrecisionMode.Scientific(-1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PrecisionMode.Scientific(13)
        }
    }

    @Test
    fun `scientific mode respects locale decimal separators`() {
        val frenchStyle = NumberFormatOptions(
            precision = PrecisionMode.Scientific(2),
            useGrouping = false,
            decimalSeparator = ',',
            groupingSeparator = '.'
        )
        assertEquals(
            "-1,00E-10",
            formatter.format(BigDecimal("-0.0000000001"), frenchStyle)
        )
    }
}
