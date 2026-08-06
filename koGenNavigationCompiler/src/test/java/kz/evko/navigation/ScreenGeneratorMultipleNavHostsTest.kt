package kz.evko.navigation

import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import kz.evko.navigation.testing.compileScreens
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Screens can be split into independent nav graphs via `navHostName`. */
class ScreenGeneratorMultipleNavHostsTest {

    @Test
    fun `screens are grouped into separate NavHost and Screens files per navHostName`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(startDestination = true, navHostName = "AuthNavHost")
            @Composable
            fun LoginScreen() {
            }

            @KoGenScreen(startDestination = true, navHostName = "MainNavHost")
            @Composable
            fun HomeScreen() {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)

        assertTrue(result.generatedFiles.containsKey("AuthNavHost.kt"))
        assertTrue(result.generatedFiles.containsKey("AuthNavHostNavigationScreens.kt"))
        assertTrue(result.generatedFiles.containsKey("MainNavHost.kt"))
        assertTrue(result.generatedFiles.containsKey("MainNavHostNavigationScreens.kt"))

        val authScreens = result.generatedFile("AuthNavHostNavigationScreens.kt")
        assertTrue(authScreens.contains("Login(\"login\")"))
        assertFalse(authScreens.contains("Home"))

        val mainScreens = result.generatedFile("MainNavHostNavigationScreens.kt")
        assertTrue(mainScreens.contains("Home(\"home\")"))
        assertFalse(mainScreens.contains("Login"))

        // Routes are shared across every nav graph in one file.
        val routes = result.generatedFile("NavigationRoutes.kt")
        assertTrue(routes.contains("ActionToLogin"))
        assertTrue(routes.contains("ActionToHome"))
    }
}
