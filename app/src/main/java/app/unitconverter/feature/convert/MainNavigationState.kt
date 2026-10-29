package app.unitconverter.feature.convert

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue

/** The four peer destinations shown in the floating bottom navigation. */
enum class MainDestination {
    CONVERT,
    UNITS,
    FAVORITES,
    HISTORY
}

/**
 * The single source of truth for bottom-navigation selection.
 *
 * C6 stored the destination as a saveable [String], derived an enum from
 * it during composition, and compared the next destination against that
 * derived snapshot inside a captured local callback. That made the
 * "already selected" branch depend on callback/recomposition timing.
 *
 * This holder keeps the enum itself in snapshot state. A click always
 * reads and writes the current value on the same stable instance, while
 * [Saver] persists only its stable enum name across recreation.
 */
class MainNavigationState(
    initialDestination: MainDestination = MainDestination.CONVERT
) {
    var destination: MainDestination by mutableStateOf(initialDestination)
        private set

    /**
     * Selects [next]. Returns true when the destination changed, or false
     * when the event was a reselection of the current destination.
     */
    fun select(next: MainDestination): Boolean {
        if (destination == next) return false
        destination = next
        return true
    }

    companion object {
        fun fromSavedValue(value: String?): MainNavigationState =
            MainNavigationState(
                initialDestination = MainDestination.entries.firstOrNull {
                    it.name == value
                } ?: MainDestination.CONVERT
            )

        val Saver: Saver<MainNavigationState, String> = Saver(
            save = { state -> state.destination.name },
            restore = { savedValue -> fromSavedValue(savedValue) }
        )
    }
}
