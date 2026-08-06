package kz.evko.navigation

import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import kz.evko.navigation.testing.compileScreens
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScreenGeneratorStartDestinationTest {

    @Test
    fun `the screen flagged startDestination = true becomes the NavHost's start destination`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen
            @Composable
            fun HomeScreen() {
            }

            @KoGenScreen(startDestination = true)
            @Composable
            fun DetailsScreen() {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertTrue(
            result.generatedFile("AppNavHost.kt")
                .contains("startDestination: String = AppNavHostNavigationScreens.Details.route"),
        )
    }

    @Test
    fun `with no screen flagged, the first declared screen is the start destination`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen
            @Composable
            fun HomeScreen() {
            }

            @KoGenScreen
            @Composable
            fun DetailsScreen() {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertTrue(
            result.generatedFile("AppNavHost.kt")
                .contains("startDestination: String = AppNavHostNavigationScreens.Home.route"),
        )
    }
}
