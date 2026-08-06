package kz.evko.navigation.annotation

import kz.evko.navigation.helpers.NavigationAnimation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * These defaults are effectively public API: the processor falls back to them whenever a
 * `@KoGenScreen(...)` usage doesn't specify a given argument. Changing any of them silently
 * changes behavior for every existing consumer of the library.
 */
class KoGenScreenTest {

    @Test
    fun `default startDestination is false`() {
        assertEquals(false, KoGenScreen().startDestination)
    }

    @Test
    fun `default navHostName is AppNavHost`() {
        assertEquals("AppNavHost", KoGenScreen().navHostName)
    }

    @Test
    fun `default animation is None`() {
        assertEquals(NavigationAnimation.None, KoGenScreen().animation)
    }
}
