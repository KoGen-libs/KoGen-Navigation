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
 */
class ScreenGeneratorViewModelAndNavControllerTest {

    private val source = """
        package test.app.screens

        import androidx.compose.runtime.Composable
        import androidx.lifecycle.ViewModel
        import androidx.navigation.NavHostController
        import kz.evko.navigation.annotation.KoGenScreen

        class ProfileViewModel : ViewModel()

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
        val result = compileScreens(source, options = mapOf("viewModelInjector" to "koin"))

        assertEquals(ExitCode.OK, result.exitCode, result.messages)

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
        // None never references the ViewModel type, so it doesn't need importing either.
        assertFalse(navHost.contains("import test.app.screens.ProfileViewModel"))
    }

    @Test
    fun `Koin injector wires koinViewModel(), importing both the function and the ViewModel type`() {
        // Regression check: generateImports() used to import only the screen function, never the
        // ViewModel class used as koinViewModel<T>()'s type argument - an unresolved reference
        // whenever the ViewModel lives in a different package than the generated NavHost (i.e.
        // always, by design - see the packageName ".navigation" suffix).
        val result = compileScreens(source, options = mapOf("viewModelInjector" to "koin"))

        assertEquals(ExitCode.OK, result.exitCode, result.messages)

        val navHost = result.generatedFile("AppNavHost.kt")
        assertTrue(navHost.contains("import org.koin.androidx.compose.koinViewModel"))
        assertTrue(navHost.contains("import test.app.screens.ProfileViewModel"))
        assertTrue(navHost.contains("viewModel = koinViewModel<ProfileViewModel>(),"))
    }

    @Test
    fun `Hilt injector wires viewModel(), importing both the composable and the ViewModel type`() {
        val result = compileScreens(source, options = mapOf("viewModelInjector" to "hilt"))

        assertEquals(ExitCode.OK, result.exitCode, result.messages)

        val navHost = result.generatedFile("AppNavHost.kt")
        assertTrue(navHost.contains("import androidx.lifecycle.viewmodel.compose.viewModel"))
        assertTrue(navHost.contains("import test.app.screens.ProfileViewModel"))
        assertTrue(navHost.contains("viewModel = viewModel<ProfileViewModel>(),"))
    }

    @Test
    fun `a ViewModel type is imported at most once, even with multiple screens sharing it`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import androidx.lifecycle.ViewModel
            import kz.evko.navigation.annotation.KoGenScreen

            class ProfileViewModel : ViewModel()

            fun <T> viewModel(): T = error("stub")

            @KoGenScreen(startDestination = true)
            @Composable
            fun HomeScreen(viewModel: ProfileViewModel = viewModel()) {
            }

            @KoGenScreen
            @Composable
            fun DetailsScreen(viewModel: ProfileViewModel = viewModel()) {
            }
            """.trimIndent(),
            options = mapOf("viewModelInjector" to "koin"),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)

        val navHost = result.generatedFile("AppNavHost.kt")
        assertEquals(1, Regex("import test\\.app\\.screens\\.ProfileViewModel\\b").findAll(navHost).count())
    }

    @Test
    fun `a ViewModel is detected by its real type, regardless of how it's obtained`() {
        // Regression check: isViewModel() used to string-match the default value expression
        // against the literal text "viewModel" - so a ViewModel obtained via hiltViewModel(),
        // koinViewModel() called directly, a custom factory, or with no default value at all was
        // silently misclassified as a regular route parameter. It's now detected from the
        // resolved type's real supertype hierarchy instead.
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import androidx.lifecycle.ViewModel
            import androidx.navigation.NavHostController
            import kz.evko.navigation.annotation.KoGenScreen

            class ProfileViewModel : ViewModel()

            fun <T> hiltViewModel(): T = error("stub")

            @KoGenScreen(startDestination = true)
            @Composable
            fun ProfileScreen(
                navController: NavHostController,
                viewModel: ProfileViewModel = hiltViewModel(),
            ) {
            }
            """.trimIndent(),
            options = mapOf("viewModelInjector" to ""),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)

        val screens = result.generatedFile("AppNavHostNavigationScreens.kt")
        // Still excluded from the route, just like the "viewModel()" case above.
        assertTrue(screens.contains("Profile(\"profile\")"))
        assertFalse(result.generatedFile("AppNavHost.kt").contains("navArgument(\"viewModel\")"))
    }

    @Test
    fun `a ViewModel is detected through multiple levels of inheritance`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import androidx.lifecycle.ViewModel
            import kz.evko.navigation.annotation.KoGenScreen

            abstract class BaseViewModel : ViewModel()
            class ProfileViewModel : BaseViewModel()

            fun <T> viewModel(): T = error("stub")

            @KoGenScreen(startDestination = true)
            @Composable
            fun ProfileScreen(viewModel: ProfileViewModel = viewModel()) {
            }
            """.trimIndent(),
            options = mapOf("viewModelInjector" to ""),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertTrue(result.generatedFile("AppNavHostNavigationScreens.kt").contains("Profile(\"profile\")"))
    }

    @Test
    fun `a type that isn't actually a ViewModel is treated as a regular route parameter`() {
        // The flip side of the fix: something that merely *looks* like a ViewModel (its default
        // value happens to be a call literally named "viewModel()") but doesn't extend
        // androidx.lifecycle.ViewModel is a real, ordinary parameter - it should end up in the
        // route, not be silently dropped.
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            class NotAViewModel

            fun <T> viewModel(): T = error("stub")

            @KoGenScreen(startDestination = true)
            @Composable
            fun ProfileScreen(config: NotAViewModel = viewModel()) {
            }
            """.trimIndent(),
            verifyCompiles = false,
        )

        val screens = result.generatedFile("AppNavHostNavigationScreens.kt")
        assertTrue(screens.contains("config={config}"))
    }
}
