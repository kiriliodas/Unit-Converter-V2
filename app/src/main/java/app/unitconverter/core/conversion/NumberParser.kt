package app.unitconverter.core.conversion

import java.math.BigDecimal

/** Outcome of parsing user-typed numeric text. */
sealed interface ParseResult {

    /** The field is blank (or only whitespace). */
    data object Empty : ParseResult

    /**
     * A valid *prefix* of a number, such as "-", "-.", "1e" or "1e-".
     * The UI must keep this text editable and must not show an error.
     */
    data object Incomplete : ParseResult

    data class Success(
        val value: BigDecimal,
        /** Canonical form: dot decimal separator, no grouping characters. */
        val normalized: String
    ) : ParseResult

    data class Invalid(val reason: InvalidReason) : ParseResult
}

enum class InvalidReason {
    INVALID_CHARACTER,
    INVALID_SIGN_PLACEMENT,
    MULTIPLE_DECIMAL_SEPARATORS,
    AMBIGUOUS_SEPARATORS,
    INVALID_GROUPING,
    INVALID_EXPONENT,
    EXPONENT_OUT_OF_RANGE,
    TOO_MANY_DIGITS
}

/**
 * Strict but editing-friendly numeric parser.
 *
 * Accepted grammar: optional sign, digits with at most one decimal
 * separator (`.` or `,`), optional grouping, optional decimal exponent.
 *
 * Separator disambiguation, by construction never a guess between two
 * plausible values:
 * - exactly one separator character overall is decimal ("1,5" = "1.5" = 1.5);
 * - a repeated separator is grouping only if it forms exact 3-digit
 *   groups ("1,000,000"), otherwise the input is invalid;
 * - both separator characters present: the one whose last occurrence is
 *   later is decimal, and the other must form exact 3-digit groups in
 *   the integer part ("1.234.567,89" and "1,234,567.89" both parse);
 * - space-like grouping characters are accepted only in the integer part
 *   and must form exact 3-digit groups ("1 234,5").
 *
 * Limits: at most [maxSignificantDigits] significant digits and an
 * exponent magnitude of at most [maxExponentMagnitude], so that a pasted
 * pathological value cannot produce unbounded [BigDecimal] work.
 */
class NumberParser(
    private val maxSignificantDigits: Int = 50,
    private val maxExponentMagnitude: Int = 999
) {
    init {
        require(maxSignificantDigits > 0) { "maxSignificantDigits must be positive" }
        require(maxExponentMagnitude in 0..9_999) {
            "maxExponentMagnitude must be between 0 and 9999"
        }
    }

    fun parse(raw: String): ParseResult {
        val text = raw.trim { it.isWhitespace() || it.isGroupingSpace() }
        if (text.isEmpty()) return ParseResult.Empty
        if (text in INCOMPLETE_LITERALS) return ParseResult.Incomplete

        // Split mantissa and exponent.
        val exponentMarkers = text.count { it == 'e' || it == 'E' }
        if (exponentMarkers > 1) {
            return ParseResult.Invalid(InvalidReason.INVALID_EXPONENT)
        }
        val exponentIndex = text.indexOfFirst { it == 'e' || it == 'E' }
        val mantissaText = if (exponentIndex >= 0) text.substring(0, exponentIndex) else text
        val exponentText = if (exponentIndex >= 0) text.substring(exponentIndex + 1) else null

        val mantissa = when (val parsed = parseMantissa(mantissaText)) {
            is MantissaParse.Invalid ->
                return ParseResult.Invalid(parsed.reason)

            MantissaParse.Incomplete ->
                return if (exponentText == null) {
                    ParseResult.Incomplete
                } else {
                    // "e10", "-.e5": an exponent cannot rescue a missing mantissa.
                    ParseResult.Invalid(InvalidReason.INVALID_EXPONENT)
                }

            is MantissaParse.Ok -> parsed
        }

        var exponent: Int? = null
        if (exponentText != null) {
            if (exponentText.isEmpty() || exponentText == "+" || exponentText == "-") {
                return ParseResult.Incomplete
            }
            if (!EXPONENT_PATTERN.matches(exponentText)) {
                return ParseResult.Invalid(InvalidReason.INVALID_EXPONENT)
            }
            val exponentDigits = exponentText.removePrefix("+").removePrefix("-")
                .trimStart('0').ifEmpty { "0" }
            // maxExponentMagnitude is at most 3 digits in practice; reject
            // anything longer without ever materializing a huge integer.
            if (exponentDigits.length > maxExponentMagnitude.toString().length) {
                return ParseResult.Invalid(InvalidReason.EXPONENT_OUT_OF_RANGE)
            }
            val magnitude = exponentDigits.toInt()
            if (magnitude > maxExponentMagnitude) {
                return ParseResult.Invalid(InvalidReason.EXPONENT_OUT_OF_RANGE)
            }
            exponent = if (exponentText.startsWith("-")) -magnitude else magnitude
        }

        val significantDigits = (mantissa.integerDigits + mantissa.fractionDigits.orEmpty())
            .trimStart('0')
        if (significantDigits.length > maxSignificantDigits) {
            return ParseResult.Invalid(InvalidReason.TOO_MANY_DIGITS)
        }

        val normalized = buildString {
            if (mantissa.negative) append('-')
            append(mantissa.integerDigits.ifEmpty { "0" })
            if (!mantissa.fractionDigits.isNullOrEmpty()) {
                append('.')
                append(mantissa.fractionDigits)
            }
            if (exponent != null) {
                append('E')
                append(exponent)
            }
        }
        return try {
            ParseResult.Success(BigDecimal(normalized), normalized)
        } catch (_: NumberFormatException) {
            // Defensive: normalized input should always be parseable. If a
            // future change breaks that invariant, report—never throw.
            ParseResult.Invalid(InvalidReason.INVALID_CHARACTER)
        }
    }

    //region Mantissa parsing

    private sealed interface MantissaParse {
        data class Ok(
            val negative: Boolean,
            val integerDigits: String,
            val fractionDigits: String?
        ) : MantissaParse

        data object Incomplete : MantissaParse
        data class Invalid(val reason: InvalidReason) : MantissaParse
    }

    private fun parseMantissa(rawMantissa: String): MantissaParse {
        var rest = rawMantissa
        var negative = false
        when {
            rest.startsWith("+") -> rest = rest.substring(1)
            rest.startsWith("-") -> {
                negative = true
                rest = rest.substring(1)
            }
        }
        if (rest.isEmpty() || rest == "." || rest == ",") return MantissaParse.Incomplete
        if ('+' in rest || '-' in rest) {
            return MantissaParse.Invalid(InvalidReason.INVALID_SIGN_PLACEMENT)
        }
        if (rest.any { !it.isDigit() && it != '.' && it != ',' && !it.isGroupingSpace() }) {
            return MantissaParse.Invalid(InvalidReason.INVALID_CHARACTER)
        }
        if (rest.none { it.isDigit() }) {
            // "..", ",," etc. — not a reachable typing prefix.
            return MantissaParse.Invalid(InvalidReason.MULTIPLE_DECIMAL_SEPARATORS)
        }

        val dotCount = rest.count { it == '.' }
        val commaCount = rest.count { it == ',' }

        return when {
            dotCount > 0 && commaCount > 0 ->
                parseWithBothSeparators(rest, negative)

            dotCount + commaCount > 1 ->
                parseWithRepeatedSeparator(rest, negative, if (dotCount > 0) '.' else ',')

            dotCount + commaCount == 1 -> {
                val separator = if (dotCount == 1) '.' else ','
                val splitAt = rest.indexOf(separator)
                val fraction = rest.substring(splitAt + 1)
                if (fraction.any { it.isGroupingSpace() }) {
                    MantissaParse.Invalid(InvalidReason.INVALID_GROUPING)
                } else {
                    when (val integer = parseGroupedInteger(rest.substring(0, splitAt))) {
                        is IntParse.Bad -> MantissaParse.Invalid(integer.reason)
                        is IntParse.Ok -> MantissaParse.Ok(negative, integer.digits, fraction)
                    }
                }
            }

            else -> when (val integer = parseGroupedInteger(rest)) {
                is IntParse.Bad -> MantissaParse.Invalid(integer.reason)
                is IntParse.Ok -> MantissaParse.Ok(negative, integer.digits, null)
            }
        }
    }

    private fun parseWithBothSeparators(rest: String, negative: Boolean): MantissaParse {
        val decimalSeparator =
            if (rest.lastIndexOf('.') > rest.lastIndexOf(',')) '.' else ','
        val groupingSeparator = if (decimalSeparator == '.') ',' else '.'
        if (rest.count { it == decimalSeparator } != 1) {
            return MantissaParse.Invalid(InvalidReason.MULTIPLE_DECIMAL_SEPARATORS)
        }
        val splitAt = rest.indexOf(decimalSeparator)
        val integerRaw = rest.substring(0, splitAt)
        val fraction = rest.substring(splitAt + 1)
        val hasSpaceGrouping = integerRaw.any { it.isGroupingSpace() }
        val hasCharGrouping = groupingSeparator in integerRaw
        if (hasSpaceGrouping && hasCharGrouping) {
            return MantissaParse.Invalid(InvalidReason.AMBIGUOUS_SEPARATORS)
        }
        if (!hasCharGrouping) {
            // The other separator only appeared after the decimal point
            // would imply two fractional parts. Reject.
            return MantissaParse.Invalid(InvalidReason.AMBIGUOUS_SEPARATORS)
        }
        if (fraction.any { !it.isDigit() }) {
            return MantissaParse.Invalid(InvalidReason.MULTIPLE_DECIMAL_SEPARATORS)
        }
        return when (
            val integer = parseGroupedInteger(integerRaw, explicitSeparator = groupingSeparator)
        ) {
            is IntParse.Bad -> MantissaParse.Invalid(
                // A grouping separator that does not group in threes means
                // the other separator was not grouping after all.
                if (integer.reason == InvalidReason.INVALID_GROUPING) {
                    InvalidReason.AMBIGUOUS_SEPARATORS
                } else {
                    integer.reason
                }
            )

            is IntParse.Ok -> MantissaParse.Ok(negative, integer.digits, fraction)
        }
    }

    private fun parseWithRepeatedSeparator(
        rest: String,
        negative: Boolean,
        separator: Char
    ): MantissaParse {
        if (rest.any { it.isGroupingSpace() }) {
            return MantissaParse.Invalid(InvalidReason.AMBIGUOUS_SEPARATORS)
        }
        // A repeated separator can only be thousands grouping, and only
        // if it groups in exact threes. Otherwise it is what it looks
        // like: several decimal separators.
        return when (val integer = parseGroupedInteger(rest, explicitSeparator = separator)) {
            is IntParse.Bad ->
                MantissaParse.Invalid(InvalidReason.MULTIPLE_DECIMAL_SEPARATORS)

            is IntParse.Ok -> MantissaParse.Ok(negative, integer.digits, null)
        }
    }

    private sealed interface IntParse {
        data class Ok(val digits: String) : IntParse
        data class Bad(val reason: InvalidReason) : IntParse
    }

    /**
     * Validates an integer part carrying either space-like grouping or one
     * explicit grouping [explicitSeparator]. Mixing the two styles is
     * ambiguous and rejected by callers before this point.
     */
    private fun parseGroupedInteger(
        raw: String,
        explicitSeparator: Char? = null
    ): IntParse {
        if (raw.isEmpty()) return IntParse.Ok("")
        val hasGrouping = raw.any { it.isGroupingSpace() || it == explicitSeparator }
        if (!hasGrouping) {
            return if (raw.all { it.isDigit() }) {
                IntParse.Ok(raw)
            } else {
                IntParse.Bad(InvalidReason.INVALID_CHARACTER)
            }
        }
        val chunks = splitKeepingEmpty(raw) { it.isGroupingSpace() || it == explicitSeparator }
        if (chunks.any { chunk -> chunk.isEmpty() || !chunk.all { it.isDigit() } }) {
            return IntParse.Bad(InvalidReason.INVALID_GROUPING)
        }
        if (chunks.first().length > 3 || chunks.drop(1).any { it.length != 3 }) {
            return IntParse.Bad(InvalidReason.INVALID_GROUPING)
        }
        return IntParse.Ok(chunks.joinToString(""))
    }

    private fun splitKeepingEmpty(text: String, isSeparator: (Char) -> Boolean): List<String> {
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (char in text) {
            if (isSeparator(char)) {
                chunks += current.toString()
                current.clear()
            } else {
                current.append(char)
            }
        }
        chunks += current.toString()
        return chunks
    }

    //endregion

    private fun Char.isGroupingSpace(): Boolean =
        this == ' ' || this == '\u00A0' || this == '\u202F' || this == '\u2009'

    private companion object {
        val INCOMPLETE_LITERALS = setOf("-", "+", ".", ",", "-.", "-,", "+.", "+,")
        val EXPONENT_PATTERN = Regex("[+-]?[0-9]+")
    }
}
