package kz.evko.navigation

import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import kz.evko.navigation.testing.compileScreens
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers `@KoGenScreen(deepLinks = [...])` - each entry becomes a `navDeepLink { uriPattern = ... }`
 * inside that screen's `composable(...)` call, and a `{placeholder}` that doesn't match any of the
 * screen's own parameters produces a KSP warning (not a build failure).
 */
class ScreenGeneratorDeepLinksTest {

    @Test
    fun `no deepLinks means no deepLinks parameter at all`() {
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
        assertFalse(navHost.contains("deepLinks"))
        assertFalse(navHost.contains("navDeepLink"))
    }

    @Test
    fun `a single deep link generates one navDeepLink entry and compiles`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(startDestination = true, deepLinks = ["myapp://home"])
            @Composable
            fun HomeScreen() {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        val navHost = result.generatedFile("AppNavHost.kt")
        val homeBlock = navHost.substringAfter("route = AppNavHostNavigationScreens.Home.route,")
            .substringBefore("composable(")
        assertTrue(homeBlock.contains("deepLinks = listOf("))
        assertTrue(homeBlock.contains("""navDeepLink { uriPattern = "myapp://home" },"""))
    }

    @Test
    fun `multiple deep links generate one navDeepLink entry each, and placeholders matching a param don't warn`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(
                startDestination = true,
                deepLinks = ["myapp://chat/{chatId}", "https://example.com/chat/{chatId}"],
            )
            @Composable
            fun ChatDetailsScreen(chatId: String) {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        val navHost = result.generatedFile("AppNavHost.kt")
        assertTrue(navHost.contains("""navDeepLink { uriPattern = "myapp://chat/{chatId}" },"""))
        assertTrue(navHost.contains("""navDeepLink { uriPattern = "https://example.com/chat/{chatId}" },"""))
        assertFalse(result.messages.contains("does not match any of its parameters"))
    }

    @Test
    fun `a placeholder not matching any parameter warns but still compiles`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(startDestination = true, deepLinks = ["myapp://chat/{chatId}"])
            @Composable
            fun HomeScreen() {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertTrue(
            result.messages.contains("'{chatId}'") &&
                result.messages.contains("does not match any of its parameters"),
            result.messages,
        )
    }
}
