package kz.evko.navigation

import kz.evko.navigation.testing.compileScreens
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * With zero `@KoGenScreen`-annotated functions in the whole compilation, the processor still
 * unconditionally emits `NavigationExtensions.kt` (the `navigateSafety`/`popBackSafety`/
 * `getResultData` helpers don't depend on any screen existing), but produces no Screens enum,
 * NavHost or NavigationRoutes file since there's nothing to generate them from.
 */
class ScreenGeneratorZeroScreensTest {

    @Test
    fun `no annotated screens still generates NavigationExtensions but nothing else`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable

            @Composable
            fun PlainComposable() {
            }
            """.trimIndent(),
            verifyCompiles = false,
        )

        assertTrue(result.generatedFiles.containsKey("NavigationExtensions.kt"))
        assertFalse(result.generatedFiles.containsKey("AppNavHost.kt"))
        assertFalse(result.generatedFiles.containsKey("AppNavHostNavigationScreens.kt"))
        assertFalse(result.generatedFiles.containsKey("NavigationRoutes.kt"))

        val extensions = result.generatedFile("NavigationExtensions.kt")
        assertTrue(extensions.contains("fun NavHostController.navigateSafety("))
        assertTrue(extensions.contains("fun NavHostController.popBackSafety("))
        assertTrue(extensions.contains("fun <T> NavHostController.getResultData("))
    }
}
