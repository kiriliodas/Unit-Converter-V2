package app.unitconverter.core.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicColorPolicyTest {
    @Test
    fun dynamicColorStartsAtAndroid12() {
        assertFalse(DynamicColorPolicy.isSupported(30))
        assertTrue(DynamicColorPolicy.isSupported(31))
        assertTrue(DynamicColorPolicy.isSupported(36))
    }

    @Test
    fun dynamicColorRequiresBothOptInAndSupportedPlatform() {
        assertFalse(DynamicColorPolicy.shouldUse(enabled = false, sdkInt = 36))
        assertFalse(DynamicColorPolicy.shouldUse(enabled = true, sdkInt = 30))
        assertTrue(DynamicColorPolicy.shouldUse(enabled = true, sdkInt = 31))
    }
}
