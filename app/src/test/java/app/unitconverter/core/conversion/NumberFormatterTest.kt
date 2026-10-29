package app.unitconverter.core.conversion

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class NumberFormatterTest {

    private val formatter = NumberFormatter()

    private fun smart(
        significantFigures: Int = 10,
        useGrouping: Boolean = true,
        decimalSeparator: Char = '.',
        groupingSeparator: Char = ','
    ) = NumberFormatOptions(
        precision = PrecisionMode.Smart(significantFigures),
        useGrouping = useGrouping,
        decimalSeparator = decimalSeparator,
        groupingSeparator = groupingSeparator
    )

    private fun fixed(
        decimalPlaces: Int,
        useGrouping: Boolean = true,
        decimalSeparator: Char = '.',
        groupingSeparator: Char = ','
    ) = NumberFormatOptions(
        precision = PrecisionMode.Fixed(decimalPlaces),
        useGrouping = useGrouping,
        decimalSeparator = decimalSeparator,
        groupingSeparator = groupingSeparator
    )

    //region Smart mode

    @Test
    fun `smart mode keeps the requested significant figures`() {
        assertEquals("3.106855961", formatter.format(BigDecimal("3.1068559611867"), smart()))
        assertEquals("123", formatter.format(BigDecimal("123.456"), smart(3)))
        assertEquals("0.00123", formatter.format(BigDecimal("0.00123456"), smart(3)))
    }

    @Test
    fun `smart mode rounds half up and trims trailing zeros`() {
        assertEquals("3", formatter.format(BigDecimal("2.5"), smart(1)))
        assertEquals("12.5", formatter.format(BigDecimal("12.5000"), smart()))
        assertEquals("1000", formatter.format(BigDecimal("999.9"), smart(3, useGrouping = false)))
    }

    @Test
    fun `smart mode groups thousands in plain range`() {
        assertEquals(
            "1,234,567.891",
            formatter.format(BigDecimal("1234567.891"), smart())
        )
        assertEquals(
            "1234567.891",
            formatter.format(BigDecimal("1234567.891"), smart(useGrouping = false))
        )
    }

    @Test
    fun `smart mode switches to scientific form at documented thresholds`() {
        assertEquals("0.0000012", formatter.format(BigDecimal("0.0000012"), smart()))
        assertEquals("1.2E-7", formatter.format(BigDecimal("0.00000012"), smart()))
        assertEquals("1E+15", formatter.format(BigDecimal("1E15"), smart()))
        assertEquals(
            "100,000,000,000,000",
            formatter.format(BigDecimal("1E14"), smart())
        )
    }

    //endregion

    //region Fixed mode

    @Test
    fun `fixed mode preserves trailing zeros exactly`() {
        assertEquals("3.100", formatter.format(BigDecimal("3.1"), fixed(3)))
        assertEquals("3", formatter.format(BigDecimal("3.1"), fixed(0)))
        assertEquals("0.00", formatter.format(BigDecimal.ZERO, fixed(2)))
    }

    @Test
    fun `fixed mode rounds half up with exact bigdecimal ties`() {
        assertEquals("2.35", formatter.format(BigDecimal("2.345"), fixed(2)))
        assertEquals("2.34", formatter.format(BigDecimal("2.344"), fixed(2)))
        assertEquals("-2.35", formatter.format(BigDecimal("-2.345"), fixed(2)))
        assertEquals("1,000.00", formatter.format(BigDecimal("999.999"), fixed(2)))
    }

    @Test
    fun `fixed mode uses scientific form only for very large magnitudes`() {
        assertEquals("1.20E+21", formatter.format(BigDecimal("1.2E21"), fixed(2)))
        assertEquals(
            "123,456,789,012,345,678.00",
            formatter.format(BigDecimal("123456789012345678"), fixed(2))
        )
    }

    //endregion

    //region Signs, zero, locales

    @Test
    fun `negative zero never displays a sign`() {
        assertEquals("0", formatter.format(BigDecimal("-0"), smart()))
        assertEquals("0.00", formatter.format(BigDecimal("-0.001"), fixed(2)))
        assertEquals("-1E-10", formatter.format(BigDecimal("-0.0000000001"), smart()))
    }

    @Test
    fun `explicit separators drive french style output`() {
        val options = fixed(2, decimalSeparator = ',', groupingSeparator = '\u202F')
        assertEquals("1\u202F234,50", formatter.format(BigDecimal("1234.5"), options))
    }

    @Test
    fun `locale factory picks the locale decimal separator`() {
        val french = NumberFormatOptions.forLocale(
            Locale.FRANCE, PrecisionMode.Fixed(2), useGrouping = false
        )
        assertEquals("1,50", formatter.format(BigDecimal("1.5"), french))
        val us = NumberFormatOptions.forLocale(
            Locale.US, PrecisionMode.Fixed(2), useGrouping = false
        )
        assertEquals("1.50", formatter.format(BigDecimal("1.5"), us))
    }

    @Test
    fun `negative numbers keep their sign in all forms`() {
        assertEquals("-40", formatter.format(BigDecimal("-40"), smart()))
        assertEquals("-1.2E-7", formatter.format(BigDecimal("-0.00000012"), smart()))
        assertEquals("-1E-10", formatter.format(BigDecimal("-0.0000000001"), smart()))
        assertEquals("-1,234.50", formatter.format(BigDecimal("-1234.5"), fixed(2)))
    }

    //endregion
}
