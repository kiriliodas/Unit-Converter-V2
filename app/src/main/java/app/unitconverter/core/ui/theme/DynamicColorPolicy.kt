package app.unitconverter.core.ui.theme

/**
 * Platform gate for Material You dynamic color.
 *
 * Android exposes dynamic light/dark color schemes from API 31
 * (Android 12). The policy is kept Android-free so the boundary can be
 * covered by ordinary JVM unit tests.
 */
object DynamicColorPolicy {
    const val MIN_SDK_INT: Int = 31

    fun isSupported(sdkInt: Int): Boolean = sdkInt >= MIN_SDK_INT

    fun shouldUse(enabled: Boolean, sdkInt: Int): Boolean =
        enabled && isSupported(sdkInt)
}
