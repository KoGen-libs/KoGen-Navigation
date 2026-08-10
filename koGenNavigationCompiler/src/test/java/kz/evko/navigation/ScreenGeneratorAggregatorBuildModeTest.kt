package kz.evko.navigation

import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile
import kz.evko.navigation.testing.compileScreenSources
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ScreenGeneratorAggregatorBuildModeTest {

    // packageName is separate from module (a Gradle module name like "feature-login" is a valid
    // *label* for error messages/the manifest's "module" field, but not a valid Kotlin package -
    // identifiers can't contain "-").
    private fun manifestJson(
        module: String,
        packageName: String,
        graphFunctionName: String,
        route: String,
        isStartDestination: Boolean,
    ) = """
        {"module":"$module","packageName":"$packageName","graphs":[
          {"graphFunctionName":"$graphFunctionName","screens":[
            {"route":"$route","name":"${route.replaceFirstChar { it.uppercase() }}","isStartDestination":$isStartDestination}
          ]}
        ]}
    """.trimIndent()

    private fun manifestsDir(vararg entries: Pair<String, String>, tempDir: Path): String {
        val dir = tempDir.toFile()
        entries.forEach { (fileName, content) -> File(dir, "$fileName.json").writeText(content) }
        return dir.absolutePath
    }

    private fun graphStub(packageName: String, functionName: String) = SourceFile.kotlin(
        "${functionName}Stub.kt",
        """
        package $packageName

        import androidx.navigation.NavGraphBuilder
        import androidx.navigation.NavHostController

        fun NavGraphBuilder.$functionName(navController: NavHostController) {
        }
        """.trimIndent(),
    )

    @Test
    fun `combines every module manifest into one NavHost`(@TempDir tempDir: Path) {
        val dir = manifestsDir(
            "feature-login" to manifestJson("feature-login", "test.featurelogin.navigation", "loginGraph", "login", isStartDestination = true),
            "feature-cart" to manifestJson("feature-cart", "test.featurecart.navigation", "cartGraph", "cart", isStartDestination = false),
            tempDir = tempDir,
        )

        val result = compileScreenSources(
            graphStub("test.featurelogin.navigation", "loginGraph"),
            graphStub("test.featurecart.navigation", "cartGraph"),
            options = mapOf(
                "buildMode" to "aggregator",
                "aggregateManifestsDir" to dir,
                "packageName" to "test.app",
            ),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        val appNavHost = result.generatedFile("AppNavHost.kt")
        assertTrue(appNavHost.contains("loginGraph(navController)"), appNavHost)
        assertTrue(appNavHost.contains("cartGraph(navController)"), appNavHost)
        assertTrue(appNavHost.contains("startDestination: String = \"login\""), appNavHost)
    }

    @Test
    fun `fails clearly on a route shared by two modules`(@TempDir tempDir: Path) {
        val dir = manifestsDir(
            "feature-login" to manifestJson("feature-login", "test.featurelogin.navigation", "loginGraph", "home", isStartDestination = true),
            "feature-cart" to manifestJson("feature-cart", "test.featurecart.navigation", "cartGraph", "home", isStartDestination = false),
            tempDir = tempDir,
        )

        val result = compileScreenSources(
            graphStub("test.featurelogin.navigation", "loginGraph"),
            graphStub("test.featurecart.navigation", "cartGraph"),
            options = mapOf(
                "buildMode" to "aggregator",
                "aggregateManifestsDir" to dir,
                "packageName" to "test.app",
            ),
            verifyCompiles = false,
        )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Duplicate route \"home\""), result.messages)
        assertTrue(result.messages.contains("feature-login"), result.messages)
        assertTrue(result.messages.contains("feature-cart"), result.messages)
    }

    @Test
    fun `fails clearly when aggregateManifestsDir isn't set`() {
        val result = compileScreenSources(
            options = mapOf("buildMode" to "aggregator"),
            verifyCompiles = false,
        )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("aggregateManifestsDir"), result.messages)
    }

    @Test
    fun `still processes its own local screens alongside the combined ones`(@TempDir tempDir: Path) {
        val dir = manifestsDir(
            "feature-login" to manifestJson("feature-login", "test.featurelogin.navigation", "loginGraph", "login", isStartDestination = true),
            tempDir = tempDir,
        )

        val result = compileScreenSources(
            SourceFile.kotlin(
                "Screens.kt",
                """
                package test.app.screens

                import androidx.compose.runtime.Composable
                import kz.evko.navigation.annotation.KoGenScreen

                @KoGenScreen
                @Composable
                fun SettingsScreen() {
                }
                """.trimIndent(),
            ),
            graphStub("test.featurelogin.navigation", "loginGraph"),
            options = mapOf(
                "buildMode" to "aggregator",
                "aggregateManifestsDir" to dir,
                "packageName" to "test.app",
                "screenSuffix" to "Screen",
            ),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertTrue(result.generatedFiles.containsKey("AppNavHostNavigationScreens.kt"))
        assertTrue(result.generatedFiles.containsKey("NavigationExtensions.kt"))
        // Own local screens go through the same graph-extension path as an external module's,
        // not a second self-contained NavHost under the same default name.
        assertTrue(result.generatedFiles.containsKey("AppNavHostGraph.kt"))
        assertTrue(result.generatedFile("AppNavHostGraph.kt").contains("SettingsScreen"))

        val appNavHost = result.generatedFile("AppNavHost.kt")
        assertFalse(appNavHost.contains("SettingsScreen"), appNavHost)
        assertTrue(appNavHost.contains("loginGraph(navController)"), appNavHost)
        assertTrue(appNavHost.contains("AppNavHostGraph(navController)"), appNavHost)
    }
}
