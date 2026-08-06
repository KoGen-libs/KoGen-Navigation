package kz.evko.navigation

import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import kz.evko.navigation.testing.compileScreens
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScreenGeneratorSmokeTest {

    @Test
    fun `generates and compiles a single no-arg screen`() {
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
        assertTrue(result.generatedFiles.containsKey("AppNavHostNavigationScreens.kt"))
        assertTrue(result.generatedFiles.containsKey("AppNavHost.kt"))
        assertTrue(result.generatedFiles.containsKey("NavigationRoutes.kt"))
        assertTrue(result.generatedFiles.containsKey("NavigationExtensions.kt"))
    }
}
