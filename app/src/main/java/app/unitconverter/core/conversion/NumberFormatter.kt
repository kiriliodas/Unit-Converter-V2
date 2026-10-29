package app.unitconverter.core.conversion

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Display precision strategy. Rounding is always HALF_UP at display time. */
sealed interface PrecisionMode {

    /**
     * At most [significantFigures] significant figures; insignificant
     * trailing zeros are removed ("12.500" becomes "12.5").
     */
    data class Smart(val significantFigures: Int = 10) : PrecisionMode {
        init {
            require(significantFigures in 1..15) {
                "Smart precision must be 1..15 significant figures"
            }
        }
    }

    /**
     * Exactly [decimalPlaces] digits after the decimal separator;
     * trailing zeros are preserved ("3.1" at 3 places becomes "3.100").
     */
    data class Fixed(val decimalPlaces: Int) : PrecisionMode {
        init {
            require(decimalPlaces in 0..12) { "Fixed precision must be 0..12 decimal places" }
        }
    }

    /**
     * Always uses normalized scientific notation for non-zero values.
     * [decimalPlaces] is the exact number of digits retained after the
     * decimal separator in the one-digit mantissa, including trailing
     * zeros ("12.3" at 2 places becomes "1.23E+1").
     */
    data class Scientific(val decimalPlaces: Int) : PrecisionMode {
        init {
            require(decimalPlaces in 0..12) {
                "Scientific precision must be 0..12 mantissa decimal places"
            }
        }
    }
}

/**
 * Concrete, locale-resolved formatting options. Separators are passed
 * explicitly so the formatter is deterministic in unit tests; production
 * code builds options with [forLocale].
 */
data class NumberFormatOptions(
    val precision: PrecisionMode = PrecisionMode.Smart(),
    val useGrouping: Boolean = true,
    val decimalSeparator: Char = '.',
    val groupingSeparator: Char = ','
) {
    init {
        require(!decimalSeparator.isDigit()) { "Decimal separator cannot be a digit" }
        require(!useGrouping || decimalSeparator != groupingSeparator) {
            "Decimal and grouping separators must differ"
        }
    }

    companion object {
        fun forLocale(
            locale: Locale,
            precision: PrecisionMode = PrecisionMode.Smart(),
            useGrouping: Boolean = true
        ): NumberFormatOptions {
            val symbols = DecimalFormatSymbols(locale)
            return NumberFormatOptions(
                precision = precision,
                useGrouping = useGrouping,
                decimalSeparator = symbols.decimalSeparator,
                groupingSeparator = symbols.groupingSeparator
            )
        }
    }
}

/**
 * Deterministic [BigDecimal] display formatter.
 *
 * Contracts:
 * - negative zero never displays a sign;
 * - Fixed mode preserves trailing zeros exactly;
 * - Scientific mode always uses normalized scientific form and keeps
 *   the requested exact number of mantissa fraction digits;
 * - grouping is applied by hand on the already-rounded plain string, so
 *   no second rounding can occur inside a locale formatter;
 * - very large/small magnitudes switch to an explicit scientific form
 *   ("1.20E+21", "1.2E-7") instead of unreadable digit runs.
 */
class NumberFormatter {

    fun format(value: BigDecimal, options: NumberFormatOptions = NumberFormatOptions()): String {
        if (value.signum() == 0) return formatZero(options)
        return when (val precision = options.precision) {
            is PrecisionMode.Smart -> formatSmart(value, precision, options)
            is PrecisionMode.Fixed -> formatFixed(value, precision, options)
            is PrecisionMode.Scientific -> scientific(value, precision.decimalPlaces, options)
        }
    }

    private fun formatZero(options: NumberFormatOptions): String = when (
        val precision = options.precision
    ) {
        is PrecisionMode.Smart -> "0"

        is PrecisionMode.Fixed -> if (precision.decimalPlaces == 0) {
            "0"
        } else {
            "0" + options.decimalSeparator + "0".repeat(precision.decimalPlaces)
        }

        is PrecisionMode.Scientific -> if (precision.decimalPlaces == 0) {
            "0E+0"
        } else {
            "0" + options.decimalSeparator +
                "0".repeat(precision.decimalPlaces) + "E+0"
        }
    }

    private fun formatSmart(
        value: BigDecimal,
        precision: PrecisionMode.Smart,
        options: NumberFormatOptions
    ): String {
        val rounded = value.round(
            MathContext(precision.significantFigures, RoundingMode.HALF_UP)
        )
        if (rounded.signum() == 0) return formatZero(options)
        val exponent = adjustedExponent(rounded)
        return if (exponent >= PLAIN_MAX_EXPONENT + 1 || exponent <= SCIENTIFIC_MIN_EXPONENT) {
            scientific(rounded.stripTrailingZeros(), null, options)
        } else {
            plain(rounded.stripTrailingZeros(), options)
        }
    }

    private fun formatFixed(
        value: BigDecimal,
        precision: PrecisionMode.Fixed,
        options: NumberFormatOptions
    ): String {
        return if (adjustedExponent(value) >= FIXED_SCIENTIFIC_EXPONENT) {
            scientific(value, precision.decimalPlaces, options)
        } else {
            val scaled = value.setScale(precision.decimalPlaces, RoundingMode.HALF_UP)
            // BigDecimal can keep a minus sign on a value that rounded to
            // zero ("-0.00"); the formatter never shows it.
            if (scaled.signum() == 0) {
                formatZero(options)
            } else {
                plain(scaled, options)
            }
        }
    }

    /** Decimal exponent of the leading digit: 1234 -> 3, 0.001 -> -3. */
    private fun adjustedExponent(value: BigDecimal): Int {
        val stripped = value.stripTrailingZeros()
        return stripped.precision() - stripped.scale() - 1
    }

    private fun plain(value: BigDecimal, options: NumberFormatOptions): String {
        val text = value.toPlainString()
        val sign = if (text.startsWith("-")) "-" else ""
        val unsigned = text.removePrefix("-")
        val dotIndex = unsigned.indexOf('.')
        val integerPart = if (dotIndex >= 0) unsigned.substring(0, dotIndex) else unsigned
        val fractionPart = if (dotIndex >= 0) unsigned.substring(dotIndex + 1) else null

        val groupedInteger = if (options.useGrouping) {
            groupThousands(integerPart, options.groupingSeparator)
        } else {
            integerPart
        }
        return buildString {
            append(sign)
            append(groupedInteger)
            if (fractionPart != null) {
                append(options.decimalSeparator)
                append(fractionPart)
            }
        }
    }

    private fun groupThousands(integerPart: String, separator: Char): String {
        if (integerPart.length <= 3) return integerPart
        val chunks = mutableListOf<String>()
        var end = integerPart.length
        while (end > 0) {
            val start = maxOf(0, end - 3)
            chunks += integerPart.substring(start, end)
            end = start
        }
        return chunks.asReversed().joinToString(separator.toString())
    }

    /**
     * Scientific form with a one-digit integer mantissa. When
     * [fixedDecimalPlaces] is non-null the mantissa keeps exactly that
     * many fraction digits; otherwise insignificant zeros are trimmed.
     */
    private fun scientific(
        value: BigDecimal,
        fixedDecimalPlaces: Int?,
        options: NumberFormatOptions
    ): String {
        val negative = value.signum() < 0
        var exponent = adjustedExponent(value)
        var mantissa = value.abs().movePointLeft(exponent)
        mantissa = if (fixedDecimalPlaces != null) {
            mantissa.setScale(fixedDecimalPlaces, RoundingMode.HALF_UP)
        } else {
            mantissa.stripTrailingZeros()
        }
        // Rounding 9.99 to fixed places can yield 10.00: re-normalize.
        if (mantissa >= BigDecimal.TEN) {
            mantissa = mantissa.movePointLeft(1)
            if (fixedDecimalPlaces != null) {
                mantissa = mantissa.setScale(fixedDecimalPlaces, RoundingMode.HALF_UP)
            }
            exponent++
        }
        val mantissaText = mantissa.toPlainString().replace('.', options.decimalSeparator)
        val exponentText = if (exponent >= 0) "+$exponent" else exponent.toString()
        return (if (negative) "-" else "") + mantissaText + "E" + exponentText
    }

    private companion object {
        /** Largest adjusted exponent still rendered in plain form (Smart). */
        const val PLAIN_MAX_EXPONENT = 14

        /** Exponents at or below this render scientifically (Smart): 1E-7. */
        const val SCIENTIFIC_MIN_EXPONENT = -7

        /** Fixed mode keeps plain form longer, then switches to scientific. */
        const val FIXED_SCIENTIFIC_EXPONENT = 21
    }
}
