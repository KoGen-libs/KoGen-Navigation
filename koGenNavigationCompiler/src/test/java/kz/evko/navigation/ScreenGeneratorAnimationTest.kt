package kz.evko.navigation

import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import kz.evko.navigation.testing.compileScreens
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers how a screen's own `animation` picks its transition, and how screens left at the
 * default (`NavigationAnimation.None`) fall back to the `defaultAnimation` KSP option.
 * The exact per-direction offset strings are already pinned in
 * `koGenNavigation`'s `NavigationAnimationTest`; here we only check the *right* animation gets
 * selected for the *right* screen.
 */
class ScreenGeneratorAnimationTest {

    @Test
    fun `a screen's own animation overrides the default, screens left at None fall back to it`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen
            import kz.evko.navigation.helpers.NavigationAnimation

            @KoGenScreen(startDestination = true, animation = NavigationAnimation.SlideLeft)
            @Composable
            fun HomeScreen() {
            }

            @KoGenScreen
            @Composable
            fun DetailsScreen() {
            }
            """.trimIndent(),
            options = mapOf("defaultAnimation" to "fade"),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        val navHost = result.generatedFile("AppNavHost.kt")

        // Anchored on "route = ..." (the composable() call), not the bare enum reference - that
        // now also appears earlier, as AppNavHost's own `startDestination` default value.
        val homeBlock = navHost.substringAfter("route = AppNavHostNavigationScreens.Home.route,")
            .substringBefore("composable(")
        assertTrue(homeBlock.contains("androidx.compose.animation.slideIn("))
        assertFalse(homeBlock.contains("androidx.compose.animation.fadeIn()"))

        val detailsBlock = navHost.substringAfter("route = AppNavHostNavigationScreens.Details.route,")
        assertTrue(detailsBlock.contains("androidx.compose.animation.fadeIn()"))
        assertFalse(detailsBlock.contains("androidx.compose.animation.slideIn("))
    }

    @Test
    fun `no per-screen animation and no defaultAnimation option means no transition code at all`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(startDestination = true)
            @Composable
            fun HomeScreen() {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        val navHost = result.generatedFile("AppNavHost.kt")
        assertFalse(navHost.contains("Transition"))
    }
}
