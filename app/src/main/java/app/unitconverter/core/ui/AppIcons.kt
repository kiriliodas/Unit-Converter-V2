package app.unitconverter.core.ui

import androidx.annotation.DrawableRes
import app.unitconverter.R
import app.unitconverter.core.model.CategoryId

/** A matching outlined/filled pair of locally bundled Material Symbols. */
data class SymbolPair(
    @param:DrawableRes val outlined: Int,
    @param:DrawableRes val filled: Int
) {
    @DrawableRes
    fun forSelected(selected: Boolean): Int = if (selected) filled else outlined
}

/**
 * Single source of truth for the Phase C6/C7 icon set.
 *
 * The project already depends on `material-icons-core`, whose small set
 * does not include most symbols required by the redesign. The used
 * Material Symbols Rounded glyphs are therefore exported as local vector
 * drawables instead of adding the generated extended icon artifact.
 */
object AppIcons {
    val Convert = SymbolPair(R.drawable.ic_symbol_sync_alt, R.drawable.ic_symbol_sync_alt_filled)
    val Units = SymbolPair(R.drawable.ic_symbol_grid_view, R.drawable.ic_symbol_grid_view_filled)
    val Favorite = SymbolPair(R.drawable.ic_symbol_favorite, R.drawable.ic_symbol_favorite_filled)
    val History = SymbolPair(R.drawable.ic_symbol_history, R.drawable.ic_symbol_history_filled)
    val Pin = SymbolPair(R.drawable.ic_symbol_push_pin, R.drawable.ic_symbol_push_pin_filled)

    @DrawableRes val Settings: Int = R.drawable.ic_symbol_settings_filled
    @DrawableRes val Swap: Int = R.drawable.ic_symbol_swap_vert
    @DrawableRes val Copy: Int = R.drawable.ic_symbol_content_copy
    @DrawableRes val Check: Int = R.drawable.ic_symbol_check
    @DrawableRes val ArrowUpward: Int = R.drawable.ic_symbol_arrow_upward
    @DrawableRes val Tune: Int = R.drawable.ic_symbol_tune
    @DrawableRes val Close: Int = R.drawable.ic_symbol_close
    @DrawableRes val Search: Int = R.drawable.ic_symbol_search
    @DrawableRes val DeleteSweep: Int = R.drawable.ic_symbol_delete_sweep
    @DrawableRes val ArrowBack: Int = R.drawable.ic_symbol_arrow_back
    @DrawableRes val ExpandMore: Int = R.drawable.ic_symbol_expand_more
    @DrawableRes val Add: Int = R.drawable.ic_symbol_add
    @DrawableRes val Remove: Int = R.drawable.ic_symbol_remove
    @DrawableRes val SearchOff: Int = R.drawable.ic_symbol_search_off
    @DrawableRes val Error: Int = R.drawable.ic_symbol_error
    @DrawableRes val Delete: Int = R.drawable.ic_symbol_delete

    private val FallbackCategory = SymbolPair(
        R.drawable.ic_symbol_category,
        R.drawable.ic_symbol_category_filled
    )

    private val CategoryIcons: Map<String, SymbolPair> = mapOf(
        "length" to SymbolPair(
            R.drawable.ic_symbol_straighten,
            R.drawable.ic_symbol_straighten_filled
        ),
        "temperature" to SymbolPair(
            R.drawable.ic_symbol_thermostat,
            R.drawable.ic_symbol_thermostat_filled
        ),
        "fuel_economy" to SymbolPair(
            R.drawable.ic_symbol_local_gas_station,
            R.drawable.ic_symbol_local_gas_station_filled
        ),
        "mass" to SymbolPair(
            R.drawable.ic_symbol_weight,
            R.drawable.ic_symbol_weight_filled
        ),
        "area" to SymbolPair(
            R.drawable.ic_symbol_square_foot,
            R.drawable.ic_symbol_square_foot_filled
        ),
        "volume" to SymbolPair(
            R.drawable.ic_symbol_view_in_ar,
            R.drawable.ic_symbol_view_in_ar_filled
        ),
        "speed" to SymbolPair(
            R.drawable.ic_symbol_speed,
            R.drawable.ic_symbol_speed_filled
        ),
        "time" to SymbolPair(
            R.drawable.ic_symbol_schedule,
            R.drawable.ic_symbol_schedule_filled
        ),
        "data_storage" to SymbolPair(
            R.drawable.ic_symbol_database,
            R.drawable.ic_symbol_database_filled
        ),
        // The design table has one combined computing entry, but the real
        // catalog deliberately keeps storage and transfer rate separate.
        "data_rate" to SymbolPair(
            R.drawable.ic_symbol_swap_horiz,
            R.drawable.ic_symbol_swap_horiz_filled
        ),
        "pressure" to SymbolPair(
            R.drawable.ic_symbol_compress,
            R.drawable.ic_symbol_compress_filled
        ),
        "energy" to SymbolPair(
            R.drawable.ic_symbol_bolt,
            R.drawable.ic_symbol_bolt_filled
        ),
        "power" to SymbolPair(
            R.drawable.ic_symbol_electric_bolt,
            R.drawable.ic_symbol_electric_bolt_filled
        ),
        "force" to SymbolPair(
            R.drawable.ic_symbol_north_east,
            R.drawable.ic_symbol_north_east_filled
        ),
        "frequency" to SymbolPair(
            R.drawable.ic_symbol_graphic_eq,
            R.drawable.ic_symbol_graphic_eq_filled
        ),
        "angle" to SymbolPair(
            R.drawable.ic_symbol_architecture,
            R.drawable.ic_symbol_architecture_filled
        )
        // Torque intentionally uses FallbackCategory, as in the design spec.
    )

    fun forCategory(categoryId: CategoryId): SymbolPair =
        CategoryIcons[categoryId.value] ?: FallbackCategory
}
