package kz.evko.navigation

import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import kz.evko.navigation.testing.compileScreens
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the `screenSuffix` KSP option, which controls how a screen function's name is turned
 * into its route/enum-entry/Action name (e.g. "HomeScreen" -> "Home").
 */
class ScreenGeneratorScreenSuffixTest {

    @Test
    fun `with no screenSuffix option, nothing is stripped from the screen's name`() {
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
            options = mapOf("screenSuffix" to ""),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertTrue(result.generatedFile("AppNavHostNavigationScreens.kt").contains("HomeScreen(\"homescreen\")"))
        assertTrue(result.generatedFile("NavigationRoutes.kt").contains("ActionToHomeScreen"))
    }

    @Test
    fun `only the last occurrence of screenSuffix is removed, so a name containing it twice keeps the first`() {
        // The exact case this option was added to fix: replaceScreenWord() used to strip every
        // occurrence of "screen" (case-insensitive) anywhere in the name, so "ScreenshotScreen"
        // became "hot". It's now anchored to the last occurrence only.
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(startDestination = true)
            @Composable
            fun ScreenshotScreen() {
            }
            """.trimIndent(),
            options = mapOf("screenSuffix" to "Screen"),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertTrue(result.generatedFile("AppNavHostNavigationScreens.kt").contains("Screenshot(\"screenshot\")"))
        assertTrue(result.generatedFile("NavigationRoutes.kt").contains("ActionToScreenshot"))
    }

    @Test
    fun `screenSuffix matches case-insensitively`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(startDestination = true)
            @Composable
            fun HomeSCREEN() {
            }
            """.trimIndent(),
            options = mapOf("screenSuffix" to "screen"),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertTrue(result.generatedFile("AppNavHostNavigationScreens.kt").contains("Home(\"home\")"))
    }

    @Test
    fun `screenSuffix matches case-insensitively regardless of which side is lowercase`() {
        // The reverse direction of the case above: a mixed-case option ("Screen") against a name
        // whose matching part is all-lowercase ("...screen"), with no word boundary between them.
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(startDestination = true)
            @Composable
            fun Abracadabrascreen() {
            }
            """.trimIndent(),
            options = mapOf("screenSuffix" to "Screen"),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertTrue(result.generatedFile("AppNavHostNavigationScreens.kt").contains("Abracadabra(\"abracadabra\")"))
    }

    @Test
    fun `a screenSuffix that isn't actually present in the name is a no-op`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(startDestination = true)
            @Composable
            fun HomePage() {
            }
            """.trimIndent(),
            options = mapOf("screenSuffix" to "Screen"),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertTrue(result.generatedFile("AppNavHostNavigationScreens.kt").contains("HomePage(\"homepage\")"))
    }
}
