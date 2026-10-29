package app.unitconverter

import android.content.Context
import android.graphics.Color
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.unitconverter.core.ui.theme.UnitConverterTheme
import app.unitconverter.feature.convert.AppPreferences
import app.unitconverter.feature.convert.AppPreferencesStore
import app.unitconverter.feature.convert.AppThemeMode
import app.unitconverter.feature.convert.SerializedAppPreferencesStore
import app.unitconverter.feature.convert.SharedPreferencesStringStorage
import java.util.Locale

/**
 * Single-activity entry point for the edge-to-edge Compose UI.
 *
 * App preferences are loaded before composition because they decide both
 * the localized base context and the light/dark theme. They are stored
 * in a separate key in the existing app-private SharedPreferences file;
 * the conversion-library payload remains untouched.
 */
class MainActivity : ComponentActivity() {

    private lateinit var appPreferencesStore: AppPreferencesStore
    private var initialPreferences: AppPreferences = AppPreferences()

    override fun attachBaseContext(newBase: Context) {
        appPreferencesStore = SerializedAppPreferencesStore(
            SharedPreferencesStringStorage(newBase.applicationContext)
        )
        initialPreferences = appPreferencesStore.load()
        super.attachBaseContext(localizedContext(newBase, initialPreferences))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val systemDarkTheme = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        applyEdgeToEdge(initialPreferences.themeMode, systemDarkTheme)
        setContent {
            var appPreferences by remember { mutableStateOf(initialPreferences) }
            val darkTheme = when (appPreferences.themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            SideEffect {
                applyEdgeToEdge(darkTheme)
            }

            UnitConverterTheme(darkTheme = darkTheme) {
                UnitConverterApp(
                    appPreferences = appPreferences,
                    onAppPreferencesChange = { updated ->
                        val languageChanged = updated.language != appPreferences.language
                        appPreferences = updated
                        appPreferencesStore.save(updated)
                        if (languageChanged) recreate()
                    }
                )
            }
        }
    }

    private fun applyEdgeToEdge(themeMode: AppThemeMode, systemDarkTheme: Boolean) {
        applyEdgeToEdge(
            darkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> systemDarkTheme
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
        )
    }

    private fun applyEdgeToEdge(darkTheme: Boolean) {
        // Register an explicit style rather than SystemBarStyle.auto:
        // AndroidX re-evaluates the style when window insets change, and
        // a manual app theme must not be overwritten by the phone mode.
        val style = if (darkTheme) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun localizedContext(base: Context, preferences: AppPreferences): Context {
        val locale: Locale? = preferences.language.locale()
        if (locale == null) {
            // Returning to "system" in the same process must also undo a
            // previously forced process-default locale.
            base.resources.configuration.locales.get(0)?.let { systemLocale ->
                Locale.setDefault(systemLocale)
            }
            return base
        }
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(configuration)
    }
}
