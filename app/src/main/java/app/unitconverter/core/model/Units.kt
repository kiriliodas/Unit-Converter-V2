package app.unitconverter.core.model

import java.math.BigDecimal

/**
 * Stable, persistence-safe category identifier.
 *
 * IDs are never localized and never renamed once shipped: favorites,
 * history, and last-used state will refer to units exclusively through
 * these identifiers. The restricted alphabet keeps IDs safe to embed in
 * delimited or key-encoded storage without escaping.
 */
@JvmInline
value class CategoryId(val value: String) {
    init {
        require(ID_PATTERN.matches(value)) { "Invalid category id: '$value'" }
    }
}

/** Stable, persistence-safe unit identifier. See [CategoryId]. */
@JvmInline
value class UnitId(val value: String) {
    init {
        require(ID_PATTERN.matches(value)) { "Invalid unit id: '$value'" }
    }
}

private val ID_PATTERN = Regex("[a-z][a-z0-9_]*")

/**
 * High-level grouping used by the category picker. Grouping is a
 * presentation concern only; it never affects conversion math.
 */
enum class CategoryGroup {
    EVERYDAY,
    SCIENCE_ENGINEERING,
    COMPUTING
}

/**
 * How a unit converts to and from its category's canonical base unit.
 *
 * Exactly three shapes exist so that special cases stay explicit and
 * cannot silently fall back to plain multiplication.
 */
sealed interface ConversionDefinition {

    /**
     * `base = value * factorToBase`. The factor must be positive and is
     * always constructed from an exact decimal string in the catalog.
     */
    data class Linear(val factorToBase: BigDecimal) : ConversionDefinition {
        init {
            require(factorToBase.signum() > 0) { "Linear factor must be positive" }
        }
    }

    /**
     * `base = (value + shiftBeforeScale) * scaleToBase`, inverted as
     * `value = base / scaleToBase - shiftBeforeScale`.
     *
     * Used by temperature (canonical base: Celsius).
     */
    data class Affine(
        val scaleToBase: BigDecimal,
        val shiftBeforeScale: BigDecimal
    ) : ConversionDefinition {
        init {
            require(scaleToBase.signum() != 0) { "Affine scale must be non-zero" }
        }
    }

    /**
     * Fuel economy, where consumption and efficiency are reciprocal
     * measures. The canonical base is liters per 100 km; [role] states
     * which reciprocal form a unit represents. The exact transform lives
     * in the conversion engine, next to its constants.
     */
    data class Fuel(val role: FuelRole) : ConversionDefinition
}

enum class FuelRole {
    /** Canonical form: liters per 100 km. */
    LITERS_PER_100_KM,

    /** `km/L = 100 / (L/100km)`. */
    KILOMETERS_PER_LITER,

    /** Miles per US gallon (1 gal = 3.785411784 L exactly). */
    MILES_PER_US_GALLON,

    /** Miles per imperial gallon (1 gal = 4.54609 L exactly). */
    MILES_PER_IMPERIAL_GALLON
}

/**
 * The set of numeric values a unit accepts as input. Anything outside the
 * rule is an explicit domain error in the engine: never an infinity,
 * never a silently clamped value.
 */
sealed interface DomainRule {

    /** Any finite real value (e.g. angles, directed forces). */
    data object AnyReal : DomainRule

    /** Zero or more (e.g. length, mass). */
    data object NonNegative : DomainRule

    /** Strictly greater than zero (fuel economy: zero has no reciprocal). */
    data object Positive : DomainRule

    /** At least [minimum] (absolute zero expressed per temperature unit). */
    data class MinimumInclusive(val minimum: BigDecimal) : DomainRule
}

/**
 * A single unit definition.
 *
 * [nameRes] is an opaque Android string resource ID supplied by the
 * catalog assembly, which keeps this core model free of Android APIs and
 * usable from plain JVM unit tests. [symbol] is the language-neutral
 * display symbol ("km"); [aliases] are extra-language search tokens
 * (spelling variants such as "metre") and are never shown directly.
 */
data class UnitDefinition(
    val id: UnitId,
    val categoryId: CategoryId,
    val nameRes: Int,
    val symbol: String,
    val aliases: Set<String> = emptySet(),
    val conversion: ConversionDefinition,
    val domain: DomainRule = DomainRule.NonNegative
) {
    init {
        require(symbol.isNotBlank()) { "Unit symbol must not be blank (${id.value})" }
        require(conversion !is ConversionDefinition.Fuel || domain == DomainRule.Positive) {
            "Fuel-economy units must use DomainRule.Positive (${id.value})"
        }
    }
}

/**
 * A conversion category: an ordered, immutable list of units that all
 * convert through one canonical base unit (the unit whose Linear factor
 * is 1, the Celsius unit for temperature, L/100km for fuel economy).
 */
data class UnitCategory(
    val id: CategoryId,
    val nameRes: Int,
    val group: CategoryGroup,
    val units: List<UnitDefinition>,
    val defaultFromId: UnitId,
    val defaultToId: UnitId
) {
    init {
        require(units.isNotEmpty()) { "Category '${id.value}' must contain units" }
        require(units.all { it.categoryId == id }) {
            "Every unit in category '${id.value}' must declare that category"
        }
        require(units.distinctBy { it.id }.size == units.size) {
            "Duplicate unit ids in category '${id.value}'"
        }
        require(units.any { it.id == defaultFromId }) {
            "Default source '${defaultFromId.value}' not in category '${id.value}'"
        }
        require(units.any { it.id == defaultToId }) {
            "Default destination '${defaultToId.value}' not in category '${id.value}'"
        }
        require(defaultFromId != defaultToId) {
            "Default pair in category '${id.value}' must use two different units"
        }
    }

    fun unit(id: UnitId): UnitDefinition? = units.firstOrNull { it.id == id }

    fun requireUnit(id: UnitId): UnitDefinition =
        checkNotNull(unit(id)) { "Unit '${id.value}' not in category '${this.id.value}'" }

    val defaultFrom: UnitDefinition get() = requireUnit(defaultFromId)
    val defaultTo: UnitDefinition get() = requireUnit(defaultToId)
}
