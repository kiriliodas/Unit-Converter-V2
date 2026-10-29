package app.unitconverter

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import app.unitconverter.feature.convert.AppPreferences
import app.unitconverter.feature.convert.ConverterScreen
import app.unitconverter.feature.update.UpdateHost

/**
 * Root composable below [MainActivity]'s theme and locale application.
 *
 * The converter entry point owns the four-destination shell: Convert,
 * Units, Favorites, and History, plus the full-screen Settings route
 * reached from Convert.
 */
@Composable
fun UnitConverterApp(
    appPreferences: AppPreferences,
    onAppPreferencesChange: (AppPreferences) -> Unit
) {
    Surface {
        UpdateHost { updateManager ->
            ConverterScreen(
                appPreferences = appPreferences,
                onAppPreferencesChange = onAppPreferencesChange,
                updateManager = updateManager
            )
        }
    }
}
