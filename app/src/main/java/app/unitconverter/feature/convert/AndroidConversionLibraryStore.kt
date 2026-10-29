package app.unitconverter.feature.convert

import android.content.Context
import android.content.SharedPreferences

/**
 * Private SharedPreferences backing for [StringStorage].
 *
 * Reads are performed by the caller on a background dispatcher. Writes
 * use `apply()` for the small serialized library: the value is updated
 * in memory immediately while Android persists it asynchronously. The
 * preferences file is app-private and contains only stable unit IDs,
 * the user's saved input values, and timestamps.
 */
class SharedPreferencesStringStorage(context: Context) : StringStorage {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(key: String): String? = preferences.getString(key, null)

    override fun write(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "conversion_library"
    }
}
