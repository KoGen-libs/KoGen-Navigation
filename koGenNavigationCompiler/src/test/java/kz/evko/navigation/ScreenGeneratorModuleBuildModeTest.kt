package kz.evko.navigation

import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import kz.evko.navigation.testing.compileScreens
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScreenGeneratorModuleBuildModeTest {

    private val twoScreens = """
        package test.feature.login

        import androidx.compose.runtime.Composable
        import kz.evko.navigation.annotation.KoGenScreen

        @KoGenScreen(startDestination = true)
        @Composable
        fun LoginScreen() {
        }

        @KoGenScreen
        @Composable
        fun ForgotPasswordScreen() {
        }
        """.trimIndent()

    @Test
    fun `generates a NavGraphBuilder extension instead of a self-contained NavHost`() {
        val result = compileScreens(
            twoScreens,
            options = mapOf("buildMode" to "module", "moduleName" to "feature-login"),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertFalse(result.generatedFiles.containsKey("AppNavHost.kt"))
        val graph = result.generatedFile("AppNavHostGraph.kt")
        assertTrue(graph.contains("fun NavGraphBuilder.AppNavHostGraph(navController: NavHostController)"), graph)
        assertFalse(graph.contains("NavHost("), graph)
        // Still every screen's own composable() entry, same as single mode would emit.
        assertTrue(graph.contains("composable("), graph)
    }

    @Test
    fun `does not generate NavigationExtensions in module mode`() {
        val result = compileScreens(
            twoScreens,
            options = mapOf("buildMode" to "module", "moduleName" to "feature-login"),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertFalse(result.generatedFiles.containsKey("NavigationExtensions.kt"))
    }

    @Test
    fun `still generates the screens enum and routes, same as single mode`() {
        val result = compileScreens(
            twoScreens,
            options = mapOf("buildMode" to "module", "moduleName" to "feature-login"),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertTrue(result.generatedFiles.containsKey("AppNavHostNavigationScreens.kt"))
        assertTrue(result.generatedFiles.containsKey("NavigationRoutes.kt"))
    }

    @Test
    fun `writes a manifest listing the graph function and every screen`() {
        val result = compileScreens(
            twoScreens,
            options = mapOf("buildMode" to "module", "moduleName" to "feature-login"),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        val manifest = result.generatedResource("META-INF/kogen-navigation/feature-login.json")
        assertTrue(manifest.contains("\"module\":\"feature-login\""), manifest)
        assertTrue(manifest.contains("\"graphFunctionName\":\"AppNavHostGraph\""), manifest)
        assertTrue(manifest.contains("\"route\":\"login\""), manifest)
        assertTrue(manifest.contains("\"route\":\"forgotpassword\""), manifest)
        assertTrue(manifest.contains("\"name\":\"Login\""), manifest)
        assertTrue(manifest.contains("\"isStartDestination\":true"), manifest)
        assertTrue(manifest.contains("\"isStartDestination\":false"), manifest)
    }

    @Test
    fun `does not write a manifest in single mode`() {
        val result = compileScreens(twoScreens)

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertTrue(result.generatedResources.isEmpty(), result.generatedResources.toString())
    }

    @Test
    fun `fails clearly when moduleName isn't set`() {
        val result = compileScreens(
            twoScreens,
            options = mapOf("buildMode" to "module"),
            verifyCompiles = false,
        )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("moduleName"), result.messages)
    }
}
