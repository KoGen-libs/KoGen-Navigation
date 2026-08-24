package kz.evko.navigation

import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile
import kz.evko.navigation.testing.compileScreenSources
import kz.evko.navigation.testing.compileScreens
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * `@KoGenTab` - the group of screens sharing a `navHostName` becomes a nested
 * `navigation(route = graph, startDestination = ...) { }` block inside one shared `NavHost`,
 * instead of either a flat merge or its own separate `NavHost`. Covers all three build modes, the
 * `shareTabGraph` local-vs-aggregator split, and the "never crash, first wins, warn" conflict policy.
 */
class ScreenGeneratorNavigationTabTest {

    private val homeTabSource = """
        package test.app.screens

        import androidx.compose.runtime.Composable
        import kz.evko.navigation.annotation.KoGenTab
        import kz.evko.navigation.annotation.KoGenScreen

        @KoGenScreen(navHostName = "home", startDestination = true)
        @KoGenTab(graph = "homeTab", startDestination = true)
        @Composable
        fun HomeScreen() {
        }

        @KoGenScreen(navHostName = "home")
        @Composable
        fun HomeDetailsScreen() {
        }

        @KoGenScreen(navHostName = "settings", startDestination = true)
        @Composable
        fun SettingsScreen() {
        }
        """.trimIndent()

    // region BuildMode.Single

    @Test
    fun `single mode nests a tab-tagged group instead of a separate NavHost, leaving a plain group untouched`() {
        val result = compileScreens(homeTabSource)

        assertEquals(ExitCode.OK, result.exitCode, result.messages)

        // The tab group gets a graph-extension, not its own standalone NavHost.
        assertFalse(result.generatedFiles.containsKey("home.kt"))
        val homeGraph = result.generatedFile("homeGraph.kt")
        assertTrue(homeGraph.contains("fun NavGraphBuilder.homeGraph(navController: NavHostController)"), homeGraph)

        // The plain group is completely unaffected: still its own self-contained NavHost.
        val settings = result.generatedFile("settings.kt")
        assertTrue(settings.contains("SettingsScreen"), settings)
        assertFalse(settings.contains("navigation("), settings)

        // The combined tabs host wraps only the tab group, defaulting to "AppTabsHost" -
        // deliberately not "AppNavHost", which an untagged default-named group already owns.
        val appNavHost = result.generatedFile("AppTabsHost.kt")
        assertTrue(appNavHost.contains("navigation(startDestination = \"home\", route = \"homeTab\")"), appNavHost)
        assertTrue(appNavHost.contains("homeGraph(navController)"), appNavHost)
        assertFalse(appNavHost.contains("SettingsScreen"), appNavHost)
        assertFalse(appNavHost.contains("settingsGraph"), appNavHost)
    }

    @Test
    fun `single mode with no tabs at all generates no combined NavHost, only the plain group's own`() {
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
        // The default-navHostName group's own self-contained NavHost - unaffected, pre-existing.
        assertTrue(result.generatedFiles.containsKey("AppNavHost.kt"))
        // But no combined tabs host, since nothing is tab-tagged.
        assertFalse(result.generatedFiles.containsKey("AppTabsHost.kt"))
    }

    @Test
    fun `single mode combines more than one tab into the same NavHost`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenTab
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(navHostName = "home", startDestination = true)
            @KoGenTab(graph = "homeTab", startDestination = true)
            @Composable
            fun HomeScreen() {
            }

            @KoGenScreen(navHostName = "profile", startDestination = true)
            @KoGenTab(graph = "profileTab", startDestination = true)
            @Composable
            fun ProfileScreen() {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        val appNavHost = result.generatedFile("AppTabsHost.kt")
        assertTrue(appNavHost.contains("navigation(startDestination = \"home\", route = \"homeTab\")"), appNavHost)
        assertTrue(appNavHost.contains("navigation(startDestination = \"profile\", route = \"profileTab\")"), appNavHost)
        assertTrue(appNavHost.contains("homeGraph(navController)"), appNavHost)
        assertTrue(appNavHost.contains("profileGraph(navController)"), appNavHost)
    }

    @Test
    fun `respects a custom tabsHostName`() {
        val result = compileScreens(
            homeTabSource,
            options = mapOf("tabsHostName" to "MainTabsHost"),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertFalse(result.generatedFiles.containsKey("AppTabsHost.kt"))
        assertTrue(result.generatedFiles.containsKey("MainTabsHost.kt"))
    }

    // endregion

    // region Conflict policy: never fail the build, first wins, warn

    @Test
    fun `conflicting graph names in the same group don't fail the build - the first wins, a warning is logged`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenTab
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(navHostName = "home", startDestination = true)
            @KoGenTab(graph = "homeTab")
            @Composable
            fun HomeScreen() {
            }

            @KoGenScreen(navHostName = "home")
            @KoGenTab(graph = "otherTab")
            @Composable
            fun HomeDetailsScreen() {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertTrue(result.messages.contains("more than one tab graph"), result.messages)
        val appNavHost = result.generatedFile("AppTabsHost.kt")
        assertTrue(appNavHost.contains("route = \"homeTab\""), appNavHost)
        assertFalse(appNavHost.contains("otherTab"), appNavHost)
    }

    @Test
    fun `more than one tab startDestination doesn't fail the build - the first wins, a warning is logged`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenTab
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(navHostName = "home", startDestination = true)
            @KoGenTab(graph = "homeTab", startDestination = true)
            @Composable
            fun HomeScreen() {
            }

            @KoGenScreen(navHostName = "home")
            @KoGenTab(graph = "homeTab", startDestination = true)
            @Composable
            fun HomeDetailsScreen() {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertTrue(result.messages.contains("more than one startDestination = true"), result.messages)
        val appNavHost = result.generatedFile("AppTabsHost.kt")
        assertTrue(appNavHost.contains("navigation(startDestination = \"home\", route = \"homeTab\")"), appNavHost)
    }

    @Test
    fun `no screen flagged as the tab's startDestination falls back to the first one, without any warning`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenTab
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(navHostName = "home")
            @KoGenTab(graph = "homeTab")
            @Composable
            fun HomeScreen() {
            }

            @KoGenScreen(navHostName = "home")
            @Composable
            fun HomeDetailsScreen() {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertFalse(result.messages.contains("startDestination"), result.messages)
        val appNavHost = result.generatedFile("AppTabsHost.kt")
        assertTrue(appNavHost.contains("navigation(startDestination = \"home\", route = \"homeTab\")"), appNavHost)
    }

    // endregion

    // region BuildMode.Module + shareTabGraph

    @Test
    fun `module mode with shareTabGraph true (default) reports the tab in the manifest and builds nothing locally`() {
        val result = compileScreens(
            homeTabSource,
            options = mapOf("buildMode" to "module", "moduleName" to "feature-home"),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertFalse(result.generatedFiles.containsKey("AppTabsHost.kt"))

        val manifest = result.generatedResource("META-INF/kogen-navigation/feature-home.json")
        assertTrue(manifest.contains("\"tabGraph\":\"homeTab\""), manifest)
        assertTrue(manifest.contains("\"isTabStartDestination\":true"), manifest)
        // The plain "settings" group is still reported too, untagged.
        assertTrue(manifest.contains("\"route\":\"settings\""), manifest)
    }

    @Test
    fun `module mode with shareTabGraph false builds the tab locally and keeps it out of the manifest`() {
        val result = compileScreens(
            homeTabSource,
            options = mapOf("buildMode" to "module", "moduleName" to "feature-home", "shareTabGraph" to "false"),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)

        val appNavHost = result.generatedFile("AppTabsHost.kt")
        assertTrue(appNavHost.contains("navigation(startDestination = \"home\", route = \"homeTab\")"), appNavHost)
        assertTrue(appNavHost.contains("homeGraph(navController)"), appNavHost)

        val manifest = result.generatedResource("META-INF/kogen-navigation/feature-home.json")
        assertFalse(manifest.contains("homeTab"), manifest)
        assertFalse(manifest.contains("\"route\":\"home\""), manifest)
        // The plain "settings" group is unaffected by shareTabGraph - still reported normally.
        assertTrue(manifest.contains("\"route\":\"settings\""), manifest)
    }

    // endregion

    // region BuildMode.Aggregator

    private fun graphManifestJson(
        graphFunctionName: String,
        route: String,
        isStartDestination: Boolean,
        tabGraph: String? = null,
        isTabStartDestination: Boolean = false,
    ) = """{"graphFunctionName":"$graphFunctionName","screens":[""" +
        """{"route":"$route","name":"${route.replaceFirstChar { it.uppercase() }}",""" +
        """"isStartDestination":$isStartDestination,"isTabStartDestination":$isTabStartDestination}]""" +
        (tabGraph?.let { ""","tabGraph":"$it"""" } ?: "") + "}"

    private fun moduleManifestJson(module: String, packageName: String, vararg graphs: String) =
        """{"module":"$module","packageName":"$packageName","graphs":[${graphs.joinToString(",")}]}"""

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
    fun `aggregator nests a tab spanning two modules into one shared navigation block`(@TempDir tempDir: Path) {
        val dir = manifestsDir(
            "feature-home" to moduleManifestJson(
                "feature-home",
                "test.featurehome.navigation",
                graphManifestJson("homeGraph", "home", isStartDestination = true, tabGraph = "homeTab", isTabStartDestination = true),
            ),
            "feature-home-settings" to moduleManifestJson(
                "feature-home-settings",
                "test.featurehomesettings.navigation",
                graphManifestJson("homeSettingsGraph", "homesettings", isStartDestination = false, tabGraph = "homeTab"),
            ),
            tempDir = tempDir,
        )

        val result = compileScreenSources(
            graphStub("test.featurehome.navigation", "homeGraph"),
            graphStub("test.featurehomesettings.navigation", "homeSettingsGraph"),
            options = mapOf(
                "buildMode" to "aggregator",
                "aggregateManifestsDir" to dir,
                "packageName" to "test.app",
            ),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        val appNavHost = result.generatedFile("AppNavHost.kt")
        assertTrue(appNavHost.contains("navigation(startDestination = \"home\", route = \"homeTab\")"), appNavHost)
        assertTrue(appNavHost.contains("homeGraph(navController)"), appNavHost)
        assertTrue(appNavHost.contains("homeSettingsGraph(navController)"), appNavHost)
    }

    @Test
    fun `aggregator keeps an untagged module flat alongside a nested tab from another module`(@TempDir tempDir: Path) {
        val dir = manifestsDir(
            "feature-home" to moduleManifestJson(
                "feature-home",
                "test.featurehome.navigation",
                graphManifestJson("homeGraph", "home", isStartDestination = true, tabGraph = "homeTab", isTabStartDestination = true),
            ),
            "feature-cart" to moduleManifestJson(
                "feature-cart",
                "test.featurecart.navigation",
                graphManifestJson("cartGraph", "cart", isStartDestination = false),
            ),
            tempDir = tempDir,
        )

        val result = compileScreenSources(
            graphStub("test.featurehome.navigation", "homeGraph"),
            graphStub("test.featurecart.navigation", "cartGraph"),
            options = mapOf(
                "buildMode" to "aggregator",
                "aggregateManifestsDir" to dir,
                "packageName" to "test.app",
            ),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        val appNavHost = result.generatedFile("AppNavHost.kt")
        assertTrue(appNavHost.contains("navigation(startDestination = \"home\", route = \"homeTab\")"), appNavHost)
        // cartGraph is called directly, never inside a navigation { } block of its own.
        assertFalse(appNavHost.contains("route = \"cart\""), appNavHost)
        assertTrue(appNavHost.contains("cartGraph(navController)"), appNavHost)
    }

    // endregion
}
