package app.unitconverter.core.conversion

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class NumberParserTest {

    private val parser = NumberParser()

    private fun value(text: String): BigDecimal = when (val result = parser.parse(text)) {
        is ParseResult.Success -> result.value
        else -> throw AssertionError("Expected '$text' to parse, got $result")
    }

    private fun assertParsesTo(expected: String, text: String) {
        assertEquals(
            "input '$text'", 0, BigDecimal(expected).compareTo(value(text))
        )
    }

    private fun assertInvalid(text: String, reason: InvalidReason) {
        assertEquals("input '$text'", ParseResult.Invalid(reason), parser.parse(text))
    }

    @Test
    fun `empty and whitespace input is empty`() {
        assertEquals(ParseResult.Empty, parser.parse(""))
        assertEquals(ParseResult.Empty, parser.parse("   "))
    }

    @Test
    fun `typing prefixes are incomplete, not errors`() {
        listOf("-", "+", ".", ",", "-.", "-,", "1e", "1e-", "1E+", "12.5e").forEach { text ->
            assertEquals("input '$text'", ParseResult.Incomplete, parser.parse(text))
        }
    }

    @Test
    fun `plain integers decimals and signs`() {
        assertParsesTo("0", "0")
        assertParsesTo("42", "  42  ")
        assertParsesTo("0.5", ".5")
        assertParsesTo("5", "5.")
        assertParsesTo("-0.5", "-.5")
        assertParsesTo("2", "+2")
        assertParsesTo("12.34", "12.34")
    }

    @Test
    fun `comma works as decimal separator`() {
        assertParsesTo("1.5", "1,5")
        assertParsesTo("-273.15", "-273,15")
        // A lone separator is decimal by the documented rule, so this is 1.
        assertParsesTo("1.000", "1,000")
    }

    @Test
    fun `scientific notation`() {
        assertParsesTo("0.00125", "1.25e-3")
        assertParsesTo("4000000", "4E6")
        assertParsesTo("150", "1.5e+2")
        assertParsesTo("15", "1,5e1")
    }

    @Test
    fun `space grouping must be exact thousands`() {
        assertParsesTo("1000000", "1 000 000")
        assertParsesTo("1234.5", "1\u00A0234,5")
        assertInvalid("1 00", InvalidReason.INVALID_GROUPING)
        assertInvalid("12 34 567", InvalidReason.INVALID_GROUPING)
        assertInvalid("1.2 3", InvalidReason.INVALID_GROUPING)
    }

    @Test
    fun `repeated separators are grouping only in exact threes`() {
        assertParsesTo("1000000", "1,000,000")
        assertParsesTo("1000000", "1.000.000")
        assertInvalid("1,2,3", InvalidReason.MULTIPLE_DECIMAL_SEPARATORS)
        assertInvalid("1.2.3", InvalidReason.MULTIPLE_DECIMAL_SEPARATORS)
        assertInvalid("1..2", InvalidReason.MULTIPLE_DECIMAL_SEPARATORS)
    }

    @Test
    fun `both separators use the later one as decimal`() {
        assertParsesTo("1234567.89", "1.234.567,89")
        assertParsesTo("1234567.89", "1,234,567.89")
        assertInvalid("1.00,5", InvalidReason.AMBIGUOUS_SEPARATORS)
        assertInvalid("1,00.5", InvalidReason.AMBIGUOUS_SEPARATORS)
    }

    @Test
    fun `invalid characters and sign placement`() {
        assertInvalid("abc", InvalidReason.INVALID_CHARACTER)
        assertInvalid("1a", InvalidReason.INVALID_CHARACTER)
        assertInvalid("1_000", InvalidReason.INVALID_CHARACTER)
        assertInvalid("1-2", InvalidReason.INVALID_SIGN_PLACEMENT)
        assertInvalid("--1", InvalidReason.INVALID_SIGN_PLACEMENT)
        assertInvalid("∞", InvalidReason.INVALID_CHARACTER)
    }

    @Test
    fun `invalid exponents`() {
        assertInvalid("e10", InvalidReason.INVALID_EXPONENT)
        assertInvalid("-.e5", InvalidReason.INVALID_EXPONENT)
        assertInvalid("1e1.5", InvalidReason.INVALID_EXPONENT)
        assertInvalid("1e5e6", InvalidReason.INVALID_EXPONENT)
        assertInvalid("1e1000", InvalidReason.EXPONENT_OUT_OF_RANGE)
        assertInvalid("1e-1000", InvalidReason.EXPONENT_OUT_OF_RANGE)
        assertParsesTo("1E999", "1e999")
        assertParsesTo("1E-999", "1e-999")
    }

    @Test
    fun `digit limit is enforced on significant digits only`() {
        val fiftyDigits = "1234567890".repeat(5)
        assertParsesTo(fiftyDigits, fiftyDigits)
        assertInvalid(fiftyDigits + "1", InvalidReason.TOO_MANY_DIGITS)
        // Leading zeros do not count toward the limit.
        assertParsesTo("1.5", "0000000000000000000000000000000000000000000000000001.5")
    }

    @Test
    fun `normalized output is canonical`() {
        val result = parser.parse("1 234,5")
        assertEquals(ParseResult.Success(BigDecimal("1234.5"), "1234.5"), result)
    }
}
