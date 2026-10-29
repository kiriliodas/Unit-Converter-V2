package app.unitconverter.feature.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNavigationStateTest {
    @Test
    fun everyDestinationCanBeSelectedFromEveryDestination() {
        MainDestination.entries.forEach { start ->
            MainDestination.entries.forEach { target ->
                val state = MainNavigationState(start)

                val changed = state.select(target)

                assertEquals(
                    "Selecting $target from $start must show $target",
                    target,
                    state.destination
                )
                assertEquals(
                    "Selecting $target from $start changed flag",
                    start != target,
                    changed
                )
            }
        }
    }

    @Test
    fun repeatedClicksOnCurrentDestinationDoNotBlockLaterNavigation() {
        MainDestination.entries.forEach { current ->
            val state = MainNavigationState(current)
            repeat(3) {
                assertFalse(state.select(current))
                assertEquals(current, state.destination)
            }

            val next = MainDestination.entries.first { it != current }
            assertTrue(state.select(next))
            assertEquals(next, state.destination)
        }
    }

    @Test
    fun requestedAndRapidNavigationSequencesRemainCoherent() {
        assertSequence(
            MainDestination.CONVERT,
            MainDestination.UNITS,
            MainDestination.CONVERT
        )
        assertSequence(
            MainDestination.CONVERT,
            MainDestination.FAVORITES,
            MainDestination.UNITS
        )
        assertSequence(
            MainDestination.HISTORY,
            MainDestination.UNITS,
            MainDestination.HISTORY
        )
        assertSequence(
            MainDestination.FAVORITES,
            MainDestination.HISTORY,
            MainDestination.CONVERT
        )

        val state = MainNavigationState()
        repeat(3) {
            MainDestination.entries.forEach { destination ->
                state.select(destination)
                assertEquals(destination, state.destination)
            }
        }
    }

    @Test
    fun savedDestinationRestoresExactlyAndInvalidValuesFallBackToConvert() {
        MainDestination.entries.forEach { destination ->
            assertEquals(
                destination,
                MainNavigationState.fromSavedValue(destination.name).destination
            )
        }
        assertEquals(
            MainDestination.CONVERT,
            MainNavigationState.fromSavedValue(null).destination
        )
        assertEquals(
            MainDestination.CONVERT,
            MainNavigationState.fromSavedValue("not-a-destination").destination
        )
    }

    private fun assertSequence(vararg destinations: MainDestination) {
        val state = MainNavigationState(destinations.first())
        destinations.drop(1).forEach { destination ->
            assertTrue(state.select(destination))
            assertEquals(destination, state.destination)
        }
    }
}
