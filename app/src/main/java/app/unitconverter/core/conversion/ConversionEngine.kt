package app.unitconverter.core.conversion

import app.unitconverter.core.model.ConversionDefinition
import app.unitconverter.core.model.DomainRule
import app.unitconverter.core.model.FuelRole
import app.unitconverter.core.model.UnitDefinition
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Outcome of one conversion. The engine never returns NaN or an infinity. */
sealed interface ConversionResult {

    data class Success(val value: BigDecimal) : ConversionResult

    /** Source and destination belong to different categories. */
    data object CategoryMismatch : ConversionResult

    /** Same category, but the two definitions use incompatible shapes. */
    data object UnsupportedPair : ConversionResult

    /** The input lies outside the source unit's accepted domain. */
    data class DomainError(val violation: DomainViolation) : ConversionResult

    /** Input or output magnitude exceeds the supported exponent range. */
    data object Overflow : ConversionResult
}

enum class DomainViolation {
    MUST_BE_NON_NEGATIVE,
    MUST_BE_POSITIVE,
    BELOW_MINIMUM
}

/**
 * Pure conversion engine over [UnitDefinition]s.
 *
 * All arithmetic uses [BigDecimal] with a 34-significant-digit context;
 * display rounding is the formatter's job and never happens here. Every
 * failure mode is a typed [ConversionResult], so there is no path to a
 * silent infinity or an accidental division by zero:
 * - fuel-economy inputs are domain-checked to be strictly positive
 *   before any reciprocal is taken;
 * - linear factors are positive by construction;
 * - affine scales are non-zero by construction.
 *
 * Converting one value is O(1); rendering "all units in this category"
 * is O(units in category). Both are far too small to leave the caller
 * thread, and the engine itself has no Android dependencies and keeps
 * no state, so it is safe to share as a single instance.
 */
class ConversionEngine(
    private val mathContext: MathContext = MathContext(34, RoundingMode.HALF_UP)
) {

    fun convert(
        value: BigDecimal,
        from: UnitDefinition,
        to: UnitDefinition
    ): ConversionResult {
        if (from.categoryId != to.categoryId) return ConversionResult.CategoryMismatch
        domainViolation(value, from.domain)?.let { return ConversionResult.DomainError(it) }
        if (!fitsInSupportedRange(value)) return ConversionResult.Overflow

        if (from.id == to.id) return ConversionResult.Success(value)

        val result = when {
            from.conversion is ConversionDefinition.Linear &&
                to.conversion is ConversionDefinition.Linear -> {
                val fromLinear = from.conversion as ConversionDefinition.Linear
                val toLinear = to.conversion as ConversionDefinition.Linear
                value.multiply(fromLinear.factorToBase, mathContext)
                    .divide(toLinear.factorToBase, mathContext)
            }

            from.conversion is ConversionDefinition.Affine &&
                to.conversion is ConversionDefinition.Affine -> {
                val fromAffine = from.conversion as ConversionDefinition.Affine
                val toAffine = to.conversion as ConversionDefinition.Affine
                val base = value.add(fromAffine.shiftBeforeScale, mathContext)
                    .multiply(fromAffine.scaleToBase, mathContext)
                base.divide(toAffine.scaleToBase, mathContext)
                    .subtract(toAffine.shiftBeforeScale, mathContext)
            }

            from.conversion is ConversionDefinition.Fuel &&
                to.conversion is ConversionDefinition.Fuel -> {
                val fromFuel = from.conversion as ConversionDefinition.Fuel
                val toFuel = to.conversion as ConversionDefinition.Fuel
                val canonical = fuelToCanonical(value, fromFuel.role)
                fuelFromCanonical(canonical, toFuel.role)
            }

            else -> return ConversionResult.UnsupportedPair
        }

        return if (fitsInSupportedRange(result)) {
            ConversionResult.Success(result)
        } else {
            ConversionResult.Overflow
        }
    }

    private fun domainViolation(value: BigDecimal, rule: DomainRule): DomainViolation? =
        when (rule) {
            DomainRule.AnyReal -> null
            DomainRule.NonNegative ->
                if (value.signum() < 0) DomainViolation.MUST_BE_NON_NEGATIVE else null

            DomainRule.Positive ->
                if (value.signum() <= 0) DomainViolation.MUST_BE_POSITIVE else null

            is DomainRule.MinimumInclusive ->
                if (value < rule.minimum) DomainViolation.BELOW_MINIMUM else null
        }

    private fun fitsInSupportedRange(value: BigDecimal): Boolean {
        if (value.signum() == 0) return true
        val stripped = value.stripTrailingZeros()
        val adjustedExponent = stripped.precision() - stripped.scale() - 1
        return adjustedExponent in -MAX_ADJUSTED_EXPONENT..MAX_ADJUSTED_EXPONENT
    }

    //region Fuel economy. Canonical base: liters per 100 km (consumption).
    // Callers guarantee value/canonical are strictly positive (the fuel
    // units all declare DomainRule.Positive), so every reciprocal below
    // has a non-zero divisor by construction.

    private fun fuelToCanonical(value: BigDecimal, role: FuelRole): BigDecimal = when (role) {
        FuelRole.LITERS_PER_100_KM -> value
        FuelRole.KILOMETERS_PER_LITER -> HUNDRED.divide(value, mathContext)
        FuelRole.MILES_PER_US_GALLON -> MPG_US_CONSTANT.divide(value, mathContext)
        FuelRole.MILES_PER_IMPERIAL_GALLON -> MPG_IMPERIAL_CONSTANT.divide(value, mathContext)
    }

    private fun fuelFromCanonical(canonical: BigDecimal, role: FuelRole): BigDecimal =
        when (role) {
            FuelRole.LITERS_PER_100_KM -> canonical
            FuelRole.KILOMETERS_PER_LITER -> HUNDRED.divide(canonical, mathContext)
            FuelRole.MILES_PER_US_GALLON -> MPG_US_CONSTANT.divide(canonical, mathContext)
            FuelRole.MILES_PER_IMPERIAL_GALLON ->
                MPG_IMPERIAL_CONSTANT.divide(canonical, mathContext)
        }

    //endregion

    private companion object {
        const val MAX_ADJUSTED_EXPONENT = 9_999

        val HUNDRED: BigDecimal = BigDecimal("100")

        // Exact definitions:
        //   1 mile = 1.609344 km
        //   1 US gallon = 3.785411784 L, 1 imperial gallon = 4.54609 L
        // mpg = (100 * litersPerGallon / kmPerMile) / (L/100km), hence the
        // constants below. They are derived, not rounded magic numbers.
        val KILOMETERS_PER_MILE: BigDecimal = BigDecimal("1.609344")
        val MPG_US_CONSTANT: BigDecimal =
            BigDecimal("378.5411784").divide(KILOMETERS_PER_MILE, MathContext(50))
        val MPG_IMPERIAL_CONSTANT: BigDecimal =
            BigDecimal("454.609").divide(KILOMETERS_PER_MILE, MathContext(50))
    }
}
