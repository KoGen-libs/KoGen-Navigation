package kz.evko.navigation

import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import kz.evko.navigation.testing.compileScreens
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers how `NavHostController` and ViewModel parameters are special-cased: excluded from the
 * route/enum/NavArgs (they're never part of the query string), and wired into the composable
 * call differently depending on the configured [kz.evko.navigation.helpers.ViewModelInjector].
 *
 * Content-only (`verifyCompiles = false`) is used for the Koin/None wiring checks: with any real
 * `ViewModelInjector`, the ViewModel type is never imported into the generated file (see the
 * "KNOWN BUG" test below), so a *full* compile of these fails for a reason unrelated to what
 * these tests are actually checking.
 */
class ScreenGeneratorViewModelAndNavControllerTest {

    private val source = """
        package test.app.screens

        import androidx.compose.runtime.Composable
        import androidx.navigation.NavHostController
        import kz.evko.navigation.annotation.KoGenScreen

        class ProfileViewModel

        fun <T> viewModel(): T = error("stub")

        @KoGenScreen(startDestination = true)
        @Composable
        fun ProfileScreen(
            navController: NavHostController,
            viewModel: ProfileViewModel = viewModel(),
            userId: String,
        ) {
        }
    """.trimIndent()

    @Test
    fun `NavHostController and ViewModel params are excluded from the route and NavArgs`() {
        val result = compileScreens(
            source,
            options = mapOf("viewModelInjector" to "koin"),
            verifyCompiles = false,
        )

        val screens = result.generatedFile("AppNavHostNavigationScreens.kt")
        // Only userId ends up in the route - navController/viewModel never do.
        assertTrue(screens.contains("Profile(\"profile?userId={userId}\")"))

        val navHost = result.generatedFile("AppNavHost.kt")
        assertFalse(navHost.contains("navArgument(\"navController\")"))
        assertFalse(navHost.contains("navArgument(\"viewModel\")"))
        assertTrue(navHost.contains("navController = navController,"))
    }

    @Test
    fun `None injector drops the ViewModel argument, relying on the screen's own default value`() {
        val result = compileScreens(source, options = mapOf("viewModelInjector" to ""))

        assertEquals(ExitCode.OK, result.exitCode, result.messages)

        val navHost = result.generatedFile("AppNavHost.kt")
        assertTrue(navHost.contains("navController = navController,"))
        assertFalse(navHost.contains("viewModel ="))
    }

    @Test
    fun `Koin injector wires koinViewModel() and imports the function (content only - see KNOWN BUG test)`() {
        val result = compileScreens(
            source,
            options = mapOf("viewModelInjector" to "koin"),
            verifyCompiles = false,
        )

        val navHost = result.generatedFile("AppNavHost.kt")
        assertTrue(navHost.contains("import org.koin.androidx.compose.koinViewModel"))
        assertTrue(navHost.contains("viewModel = koinViewModel<ProfileViewModel>(),"))
    }

    @Test
    fun `KNOWN BUG - the ViewModel's own type is never imported, so Koin-Hilt injection doesn't resolve`() {
        // generateImports() imports the *screen function* but never the ViewModel class used as
        // koinViewModel<T>()/viewModel<T>()'s type argument. Since the generated NavHost lives in
        // a different package (`<screens>.navigation`) than the screen/ViewModel by design, that
        // type argument is an unresolved reference for any real project layout. If this is fixed
        // (e.g. importing the ViewModel's declaration like screen functions already are), update
        // this test to assert ExitCode.OK instead.
        val result = compileScreens(source, options = mapOf("viewModelInjector" to "koin"))

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Unresolved reference 'ProfileViewModel'"))
    }

    @Test
    fun `KNOWN BUG - Hilt injector emits viewModel() with no import, so it never resolves`() {
        // ViewModelInjector.Hilt.injectorImport is "" - the generated NavHost calls a bare
        // `viewModel<T>()` with nothing importing it into scope, so any consumer configured for
        // Hilt gets "unresolved reference: viewModel" on every screen with a ViewModel parameter.
        // If this is fixed (e.g. by importing androidx.lifecycle.viewmodel.compose.viewModel),
        // update this test to assert ExitCode.OK instead.
        val result = compileScreens(source, options = mapOf("viewModelInjector" to "hilt"))

        assertTrue(result.generatedFile("AppNavHost.kt").contains("viewModel = viewModel<ProfileViewModel>(),"))
        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Unresolved reference"))
    }
}
