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
 * `@KoGenTab` - used *instead of* `@KoGenScreen`, never alongside it (see its own doc for why:
 * `@KoGenScreen` alone already registers a screen's `composable(...)` entry - stacking a second
 * annotation on top wouldn't add a second one, only extra, confusing bookkeeping). [KoGenTab.graph]
 * is a *shared grouping key*, not a per-screen identifier: every screen sharing one `graph` value
 * is nested, as a sibling of every other screen sharing it, inside *one* shared
 * `navigation(route = graph, startDestination = ...) { }` block - one bottom bar with N tabs is one
 * nested graph with N sibling destinations, not N separate nested graphs. Each screen still gets
 * its own generated, typed `ActionToTab<Screen>` (a `TabNavigationAction`, deliberately unrelated to a
 * plain screen's own `NavigationAction`) targeting *that screen's own route* - switching tabs
 * navigates to the individual tab, never to the shared graph's own route - to pass to the real,
 * hand-written `navigateSafety(action: TabNavigationAction)` overload in `koGenNavigation` itself -
 * not generated per project, same as `navigateSafety(action: NavigationAction, ...)`.
 *
 * Covers all three build modes, the `shareTabGraph` local-vs-aggregator split, the "never crash,
 * first wins, warn" conflict policy, and - the one a stacked-annotation design got wrong - that the
 * outer `NavHost`'s own default `startDestination` never ends up pointing at a screen that lives
 * inside a tab, only ever at the shared graph's own route.
 */
class ScreenGeneratorNavigationTabTest {

    private val homeTabSource = """
        package test.app.screens

        import androidx.compose.runtime.Composable
        import kz.evko.navigation.annotation.KoGenTab
        import kz.evko.navigation.annotation.KoGenScreen

        @KoGenTab(graph = "homeTab", startDestination = true)
        @Composable
        fun HomeScreen() {
        }

        @KoGenTab(graph = "homeTab")
        @Composable
        fun HomeDetailsScreen() {
        }

        @KoGenScreen(startDestination = true)
        @Composable
        fun SettingsScreen() {
        }
        """.trimIndent()

    // region Not stacked with @KoGenScreen

    @Test
    fun `a KoGenTab screen needs no KoGenScreen and registers exactly one composable entry`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenTab

            @KoGenTab(graph = "homeTab", startDestination = true)
            @Composable
            fun HomeScreen() {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        val graph = result.generatedFile("homeTabGraph.kt")
        assertEquals(1, graph.split("composable(").size - 1, graph)
        assertTrue(graph.contains("HomeScreen()"), graph)
    }

    @Test
    fun `stacking both annotations on one screen doesn't register it twice - KoGenScreen wins, a warning is logged`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenTab
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(startDestination = true)
            @KoGenTab(graph = "homeTab", startDestination = true)
            @Composable
            fun HomeScreen() {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertTrue(result.messages.contains("carries both @KoGenScreen and @KoGenTab"), result.messages)
        assertFalse(result.generatedFiles.containsKey("AppTabsHost.kt"))
        assertFalse(result.generatedFiles.containsKey("homeTabGraph.kt"))
        val appNavHost = result.generatedFile("AppNavHost.kt")
        assertEquals(1, appNavHost.split("composable(").size - 1, appNavHost)
    }

    // endregion

    // region BuildMode.Single

    @Test
    fun `single mode nests a tab and generates its screens' typed ActionToTab, leaving a plain group untouched`() {
        val result = compileScreens(homeTabSource)

        assertEquals(ExitCode.OK, result.exitCode, result.messages)

        assertFalse(result.generatedFiles.containsKey("homeTab.kt"))
        val homeGraph = result.generatedFile("homeTabGraph.kt")
        assertTrue(homeGraph.contains("fun NavGraphBuilder.homeTabGraph(navController: NavHostController)"), homeGraph)
        // Both screens sharing graph = "homeTab" are siblings in the *same* graph-extension
        // function, not each wrapped in its own nested graph.
        assertTrue(homeGraph.contains("HomeScreen()"), homeGraph)
        assertTrue(homeGraph.contains("HomeDetailsScreen()"), homeGraph)

        // The plain group is completely unaffected: still its own self-contained NavHost.
        val settings = result.generatedFile("AppNavHost.kt")
        assertTrue(settings.contains("SettingsScreen"), settings)
        assertFalse(settings.contains("navigation("), settings)

        val appTabsHost = result.generatedFile("AppTabsHost.kt")
        // One shared nested graph for both tab screens - not one per screen.
        assertEquals(1, appTabsHost.split("navigation(").size - 1, appTabsHost)
        assertTrue(appTabsHost.contains("navigation(startDestination = \"home\", route = \"homeTab\")"), appTabsHost)
        assertTrue(appTabsHost.contains("homeTabGraph(navController)"), appTabsHost)
        assertFalse(appTabsHost.contains("SettingsScreen"), appTabsHost)

        // navigateSafety(action: TabNavigationAction) itself is NOT generated - it's a real,
        // hand-written function in koGenNavigation; only each typed action is per-project.
        assertFalse(appTabsHost.contains("fun NavHostController.navigateSafety"), appTabsHost)
        // No action of any kind lives in AppTabsHost.kt any more - see TabRoutes.kt below.
        assertFalse(appTabsHost.contains("TabNavigationAction"), appTabsHost)

        // One ActionToTab<Screen> per screen sharing the graph - keyed by that screen's own route,
        // not the shared graph's route - since switching tabs targets the individual screen.
        val tabRoutes = result.generatedFile("TabRoutes.kt")
        assertTrue(tabRoutes.contains("data object ActionToTabHome : TabNavigationAction(route = \"home\")"), tabRoutes)
        assertTrue(
            tabRoutes.contains("data object ActionToTabHomeDetails : TabNavigationAction(route = \"homedetails\")"),
            tabRoutes,
        )
    }

    @Test
    fun `single mode with no tabs at all generates no combined tabs host`() {
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
        assertTrue(result.generatedFiles.containsKey("AppNavHost.kt"))
        assertFalse(result.generatedFiles.containsKey("AppTabsHost.kt"))
    }

    @Test
    fun `single mode combines two distinct tab bars into the same host, each its own nested graph`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenTab

            @KoGenTab(graph = "homeTab", startDestination = true)
            @Composable
            fun HomeScreen() {
            }

            @KoGenTab(graph = "profileTab", startDestination = true)
            @Composable
            fun ProfileScreen() {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        val appTabsHost = result.generatedFile("AppTabsHost.kt")
        assertTrue(appTabsHost.contains("navigation(startDestination = \"home\", route = \"homeTab\")"), appTabsHost)
        assertTrue(appTabsHost.contains("navigation(startDestination = \"profile\", route = \"profileTab\")"), appTabsHost)
        // navigateSafety(action: TabNavigationAction) itself is never generated, per tab or otherwise.
        assertFalse(appTabsHost.contains("fun NavHostController.navigateSafety"), appTabsHost)

        val tabRoutes = result.generatedFile("TabRoutes.kt")
        assertTrue(tabRoutes.contains("data object ActionToTabHome : TabNavigationAction(route = \"home\")"), tabRoutes)
        assertTrue(tabRoutes.contains("data object ActionToTabProfile : TabNavigationAction(route = \"profile\")"), tabRoutes)
    }

    @Test
    fun `two tabs sharing one graph become siblings in one shared nested graph, each keeping its own route and ActionToTab`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenTab

            @KoGenTab(graph = "mainTabs", startDestination = true)
            @Composable
            fun HomeScreen() {
            }

            @KoGenTab(graph = "mainTabs")
            @Composable
            fun ProfileScreen() {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)

        // Exactly one nested graph for the whole bar, not one per tab.
        val appTabsHost = result.generatedFile("AppTabsHost.kt")
        assertEquals(1, appTabsHost.split("navigation(").size - 1, appTabsHost)
        assertTrue(appTabsHost.contains("navigation(startDestination = \"home\", route = \"mainTabs\")"), appTabsHost)
        assertTrue(appTabsHost.contains("mainTabsGraph(navController)"), appTabsHost)

        // Both screens are siblings inside that one graph-extension function.
        val mainTabsGraph = result.generatedFile("mainTabsGraph.kt")
        assertTrue(mainTabsGraph.contains("HomeScreen()"), mainTabsGraph)
        assertTrue(mainTabsGraph.contains("ProfileScreen()"), mainTabsGraph)

        // Switching tabs targets each tab's own route - never the shared graph's own route.
        val tabRoutes = result.generatedFile("TabRoutes.kt")
        assertTrue(tabRoutes.contains("data object ActionToTabHome : TabNavigationAction(route = \"home\")"), tabRoutes)
        assertTrue(tabRoutes.contains("data object ActionToTabProfile : TabNavigationAction(route = \"profile\")"), tabRoutes)
        assertFalse(tabRoutes.contains("mainTabs"), tabRoutes)
    }

    @Test
    fun `respects a custom tabsHostName`() {
        val result = compileScreens(homeTabSource, options = mapOf("tabsHostName" to "MainTabsHost"))

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertFalse(result.generatedFiles.containsKey("AppTabsHost.kt"))
        assertTrue(result.generatedFiles.containsKey("MainTabsHost.kt"))
    }

    // endregion

    // region The outer NavHost's own default startDestination never points inside a tab

    @Test
    fun `with only tabs, the outer default startDestination is the tab's own route, not a screen inside it`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenTab

            @KoGenTab(graph = "homeTab", startDestination = true)
            @Composable
            fun HomeScreen() {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        val appTabsHost = result.generatedFile("AppTabsHost.kt")
        // The outer host's own default - never "home" (the screen, unreachable at this scope).
        assertTrue(appTabsHost.contains("startDestination: String = \"homeTab\""), appTabsHost)
    }

    // endregion

    // region Conflict policy: never fail the build, first wins, warn

    @Test
    fun `more than one tab startDestination doesn't fail the build - the first wins, a warning is logged`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenTab

            @KoGenTab(graph = "homeTab", startDestination = true)
            @Composable
            fun HomeScreen() {
            }

            @KoGenTab(graph = "homeTab", startDestination = true)
            @Composable
            fun HomeDetailsScreen() {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertTrue(result.messages.contains("more than one startDestination = true"), result.messages)
        val appTabsHost = result.generatedFile("AppTabsHost.kt")
        assertTrue(appTabsHost.contains("navigation(startDestination = \"home\", route = \"homeTab\")"), appTabsHost)
    }

    @Test
    fun `no screen flagged as the tab's startDestination falls back to the first one, without any warning`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenTab

            @KoGenTab(graph = "homeTab")
            @Composable
            fun HomeScreen() {
            }

            @KoGenTab(graph = "homeTab")
            @Composable
            fun HomeDetailsScreen() {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertFalse(result.messages.contains("startDestination"), result.messages)
        val appTabsHost = result.generatedFile("AppTabsHost.kt")
        assertTrue(appTabsHost.contains("navigation(startDestination = \"home\", route = \"homeTab\")"), appTabsHost)
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
        // The plain "settings" screen is still reported too, untagged.
        assertTrue(manifest.contains("\"route\":\"settings\""), manifest)
    }

    @Test
    fun `module mode with shareTabGraph false builds the tab locally and keeps it out of the manifest`() {
        val result = compileScreens(
            homeTabSource,
            options = mapOf("buildMode" to "module", "moduleName" to "feature-home", "shareTabGraph" to "false"),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)

        val appTabsHost = result.generatedFile("AppTabsHost.kt")
        assertTrue(appTabsHost.contains("navigation(startDestination = \"home\", route = \"homeTab\")"), appTabsHost)

        val tabRoutes = result.generatedFile("TabRoutes.kt")
        assertTrue(tabRoutes.contains("data object ActionToTabHome : TabNavigationAction(route = \"home\")"), tabRoutes)

        val manifest = result.generatedResource("META-INF/kogen-navigation/feature-home.json")
        assertFalse(manifest.contains("homeTab"), manifest)
        // The plain "settings" screen is unaffected by shareTabGraph - still reported normally.
        assertTrue(manifest.contains("\"route\":\"settings\""), manifest)
    }

    // endregion

    // region BuildMode.Aggregator

    private fun graphManifestJson(
        graphFunctionName: String,
        route: String,
        isStartDestination: Boolean,
        tabGraph: String? = null,
    ) = """{"graphFunctionName":"$graphFunctionName","screens":[""" +
        """{"route":"$route","name":"${route.replaceFirstChar { it.uppercase() }}","isStartDestination":$isStartDestination}]""" +
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
    fun `aggregator nests a tab spanning two modules into one shared navigation block`(
        @TempDir tempDir: Path,
    ) {
        val dir = manifestsDir(
            "feature-home" to moduleManifestJson(
                "feature-home",
                "test.featurehome.navigation",
                graphManifestJson("homeTabGraph", "home", isStartDestination = true, tabGraph = "homeTab"),
            ),
            "feature-home-settings" to moduleManifestJson(
                "feature-home-settings",
                "test.featurehomesettings.navigation",
                graphManifestJson("homeSettingsGraph", "homesettings", isStartDestination = false, tabGraph = "homeTab"),
            ),
            tempDir = tempDir,
        )

        val result = compileScreenSources(
            graphStub("test.featurehome.navigation", "homeTabGraph"),
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
        assertTrue(appNavHost.contains("homeTabGraph(navController)"), appNavHost)
        assertTrue(appNavHost.contains("homeSettingsGraph(navController)"), appNavHost)
        // Each tab screen's own ActionToTab is generated locally, per module (see the single/module
        // mode tests above) - not here, since the aggregator only ever combines already-built
        // manifests, never re-derives per-screen actions from them.
        assertFalse(appNavHost.contains("TabNavigationAction"), appNavHost)
        // The outer default is the tab's own route, not the screen's - even across modules.
        assertTrue(appNavHost.contains("startDestination: String = \"homeTab\""), appNavHost)
    }

    @Test
    fun `aggregator keeps an untagged module flat alongside a nested tab from another module`(@TempDir tempDir: Path) {
        val dir = manifestsDir(
            "feature-home" to moduleManifestJson(
                "feature-home",
                "test.featurehome.navigation",
                graphManifestJson("homeTabGraph", "home", isStartDestination = true, tabGraph = "homeTab"),
            ),
            "feature-cart" to moduleManifestJson(
                "feature-cart",
                "test.featurecart.navigation",
                graphManifestJson("cartGraph", "cart", isStartDestination = false),
            ),
            tempDir = tempDir,
        )

        val result = compileScreenSources(
            graphStub("test.featurehome.navigation", "homeTabGraph"),
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

    @Test
    fun `an explicit plain screen still wins the combined app's default over an unflagged tab`(@TempDir tempDir: Path) {
        val dir = manifestsDir(
            "feature-settings" to moduleManifestJson(
                "feature-settings",
                "test.featuresettings.navigation",
                graphManifestJson("settingsGraph", "settings", isStartDestination = true),
            ),
            "feature-home" to moduleManifestJson(
                "feature-home",
                "test.featurehome.navigation",
                graphManifestJson("homeTabGraph", "home", isStartDestination = false, tabGraph = "homeTab"),
            ),
            tempDir = tempDir,
        )

        val result = compileScreenSources(
            graphStub("test.featuresettings.navigation", "settingsGraph"),
            graphStub("test.featurehome.navigation", "homeTabGraph"),
            options = mapOf(
                "buildMode" to "aggregator",
                "aggregateManifestsDir" to dir,
                "packageName" to "test.app",
            ),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        val appNavHost = result.generatedFile("AppNavHost.kt")
        assertTrue(appNavHost.contains("startDestination: String = \"settings\""), appNavHost)
    }

    // endregion
}
