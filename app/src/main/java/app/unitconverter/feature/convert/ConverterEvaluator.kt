package app.unitconverter.feature.convert

import app.unitconverter.core.catalog.UnitCatalog
import app.unitconverter.core.conversion.ConversionEngine
import app.unitconverter.core.conversion.ConversionResult
import app.unitconverter.core.conversion.DomainViolation
import app.unitconverter.core.conversion.InvalidReason
import app.unitconverter.core.conversion.NumberParser
import app.unitconverter.core.conversion.ParseResult
import app.unitconverter.core.model.CategoryId
import app.unitconverter.core.model.DomainRule
import app.unitconverter.core.model.UnitCategory
import app.unitconverter.core.model.UnitDefinition
import app.unitconverter.core.model.UnitId
import java.math.BigDecimal

/** What the result area should show for the current input. */
sealed interface ResultState {

    /** No input yet: show a neutral placeholder. */
    data object Empty : ResultState

    /** A valid typing prefix such as "-": placeholder, but no error. */
    data object Incomplete : ResultState

    /** The text is not a number the parser accepts. */
    data class Invalid(val reason: InvalidReason) : ResultState

    /** The number is outside the source unit's accepted domain. */
    data class Domain(val violation: DomainViolation) : ResultState

    /** The number or its result exceeds the supported magnitude range. */
    data object Overflow : ResultState

    /**
     * A successful conversion. [value] is the raw engine result,
     * [rate] is the converted value of exactly 1 source unit (for the
     * "1 km = 0.621371 mi" caption), when that rate exists.
     */
    data class Value(val value: BigDecimal, val rate: BigDecimal?) : ResultState
}

/** One row of the "all conversions" list; [value] is null when that
 * particular conversion is unavailable (domain/overflow). */
data class UnitConversionRow(
    val unit: UnitDefinition,
    val value: BigDecimal?
)

/**
 * Everything the converter screen renders, derived from the four pieces
 * of user state (category, source unit, destination unit, input text).
 */
data class ConverterUiState(
    val category: UnitCategory,
    val from: UnitDefinition,
    val to: UnitDefinition,
    val inputText: String,
    val result: ResultState,
    val rows: List<UnitConversionRow>,
    /** True when [rows] show equivalents of 1 source unit because there
     * is no usable input value yet. */
    val rowsUseUnitReference: Boolean,
    /** Whether a sign toggle makes sense for the source unit. */
    val negativeInputAllowed: Boolean
)

/**
 * Pure, synchronous derivation of [ConverterUiState] from the existing
 * [NumberParser] and [ConversionEngine]. It contains no Android or
 * Compose types, so the whole input -> result behavior (including error
 * mapping) is covered by plain JVM unit tests.
 *
 * Unknown or stale identifiers never crash evaluation: they fall back
 * to the first category and that category's default pair. This keeps
 * restored UI state safe across catalog changes.
 */
class ConverterEvaluator(
    private val catalog: UnitCatalog,
    private val parser: NumberParser = NumberParser(),
    private val engine: ConversionEngine = ConversionEngine()
) {

    fun evaluate(
        categoryId: CategoryId,
        fromId: UnitId,
        toId: UnitId,
        inputText: String
    ): ConverterUiState {
        val category = catalog.category(categoryId) ?: catalog.categories.first()
        val from = category.unit(fromId) ?: category.defaultFrom
        val to = category.unit(toId) ?: category.defaultTo

        return when (val parsed = parser.parse(inputText)) {
            ParseResult.Empty -> state(
                category, from, to, inputText,
                result = ResultState.Empty,
                reference = BigDecimal.ONE,
                rowsUseUnitReference = true
            )

            ParseResult.Incomplete -> state(
                category, from, to, inputText,
                result = ResultState.Incomplete,
                reference = BigDecimal.ONE,
                rowsUseUnitReference = true
            )

            is ParseResult.Invalid -> state(
                category, from, to, inputText,
                result = ResultState.Invalid(parsed.reason),
                reference = BigDecimal.ONE,
                rowsUseUnitReference = true
            )

            is ParseResult.Success -> {
                val result = when (val converted = engine.convert(parsed.value, from, to)) {
                    is ConversionResult.Success -> ResultState.Value(
                        value = converted.value,
                        rate = if (parsed.value.compareTo(BigDecimal.ONE) == 0) {
                            converted.value
                        } else {
                            (engine.convert(BigDecimal.ONE, from, to)
                                as? ConversionResult.Success)?.value
                        }
                    )

                    is ConversionResult.DomainError -> ResultState.Domain(converted.violation)
                    ConversionResult.Overflow -> ResultState.Overflow
                    // Unreachable for catalog categories, which never
                    // mix categories or conversion shapes. If a future
                    // catalog broke that rule, degrade to a generic
                    // invalid state instead of a misleading number:
                    ConversionResult.CategoryMismatch,
                    ConversionResult.UnsupportedPair ->
                        ResultState.Invalid(InvalidReason.INVALID_CHARACTER)
                }
                state(
                    category, from, to, inputText,
                    result = result,
                    reference = parsed.value,
                    rowsUseUnitReference = false,
                    destinationValue = (result as? ResultState.Value)?.value
                )
            }
        }
    }

    private fun state(
        category: UnitCategory,
        from: UnitDefinition,
        to: UnitDefinition,
        inputText: String,
        result: ResultState,
        reference: BigDecimal,
        rowsUseUnitReference: Boolean,
        destinationValue: BigDecimal? = null
    ): ConverterUiState {
        val rows = category.units.map { unit ->
            val value = if (unit.id == to.id && destinationValue != null) {
                destinationValue
            } else {
                when (val converted = engine.convert(reference, from, unit)) {
                    is ConversionResult.Success -> converted.value
                    else -> null
                }
            }
            UnitConversionRow(unit = unit, value = value)
        }
        return ConverterUiState(
            category = category,
            from = from,
            to = to,
            inputText = inputText,
            result = result,
            rows = rows,
            rowsUseUnitReference = rowsUseUnitReference,
            negativeInputAllowed = from.domain is DomainRule.MinimumInclusive ||
                from.domain is DomainRule.AnyReal
        )
    }
}
