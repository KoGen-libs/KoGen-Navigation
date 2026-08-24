package kz.evko.navigation.contentGenerators

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import kz.evko.navigation.manifest.GraphManifestEntry
import kz.evko.navigation.manifest.ModuleManifest

/**
 * Builds the combined `@Composable fun <hostName>(navController, modifier, startDestination)` an
 * aggregator (`BuildMode.Aggregator`) generates from every `BuildMode.Module` manifest it
 * collected - one `NavHost { }` calling each manifest's own graph-extension function(s), so every
 * module's screens end up sharing this one graph/back stack instead of each getting their own.
 */
internal class AggregatorContentGenerator(
    private val packageName: String,
) {
    private val composableAnnotation = ClassName("androidx.compose.runtime", "Composable")
    private val modifierType = ClassName("androidx.compose.ui", "Modifier")
    private val navHostControllerType = ClassName("androidx.navigation", "NavHostController")
    private val navHostMember = MemberName("androidx.navigation.compose", "NavHost")
    private val navigationMember = MemberName("androidx.navigation.compose", "navigation")

    // findStartDestination() lives in NavGraph's own companion, not as a plain top-level function
    // in the package - importing it as if it were (the natural-looking MemberName("androidx.navigation",
    // "findStartDestination")) silently resolves to nothing, and the knock-on type-inference
    // failure was confusing enough to be worth this comment: `popUpTo(...)`'s argument becomes an
    // error type, which somehow still type-checks the call but resolves its trailing lambda's
    // implicit receiver to the *outer* NavOptionsBuilder instead of PopUpToBuilder - so `saveState`
    // inside it fails as "private in NavOptionsBuilder" instead of the actually-missing-symbol
    // error you'd expect.
    private val findStartDestinationMember = MemberName(ClassName("androidx.navigation", "NavGraph", "Companion"), "findStartDestination")
    private val tabNavigationActionType = ClassName("kz.evko.navigation.routes", "TabNavigationAction")

    /** One manifest's [GraphManifestEntry], plus that manifest's own package (to qualify [GraphManifestEntry.graphFunctionName] with). */
    private data class Entry(val packageName: String, val graph: GraphManifestEntry)

    /**
     * @param startDestinationRoute The route to default the generated function's own
     *   `startDestination` parameter to (see `ManifestValidator.resolveStartDestinationRoute`) -
     *   `null` makes it a required parameter instead, since there's nothing to default to.
     * @param tabStartDestinations Every tab's own `startDestination` route, keyed by
     *   [GraphManifestEntry.tabGraph] (see `ManifestValidator.resolveTabStartDestinations`) - every
     *   non-null `tabGraph` across [manifests] must have an entry here.
     */
    fun generateAppNavHost(
        manifests: List<ModuleManifest>,
        hostName: String,
        startDestinationRoute: String?,
        tabStartDestinations: Map<String, String>,
    ): FileSpec {
        val startDestinationParam = ParameterSpec.builder("startDestination", STRING).apply {
            if (startDestinationRoute != null) defaultValue("%S", startDestinationRoute)
        }.build()

        val hostFunction = FunSpec.builder(hostName)
            .addAnnotation(composableAnnotation)
            .addParameter(ParameterSpec.builder("modifier", modifierType).defaultValue("%T", modifierType).build())
            .addParameter("navController", navHostControllerType)
            .addParameter(startDestinationParam)
            .addCode(generateBody(manifests, tabStartDestinations))
            .build()

        val fileBuilder = FileSpec.builder(packageName, hostName)
            .addFunction(hostFunction)
        if (tabStartDestinations.isNotEmpty()) {
            fileBuilder.addFunction(generateTabNavigateSafetyOverload())
            tabStartDestinations.keys.forEach { tabGraph -> fileBuilder.addType(generateTabAction(tabGraph)) }
        }
        return fileBuilder.build()
    }

    /**
     * `fun NavHostController.navigateSafety(action: TabNavigationAction)` - overloads the same
     * name a screen's own `navigateSafety(action: NavigationAction, ...)` uses (see
     * `RoutesListGenerator.generateExtensions`), so there's one call to remember regardless of
     * what's being navigated to - Kotlin picks the right overload from [TabNavigationAction] vs
     * `NavigationAction` being unrelated types, the same way it always resolves an overload by
     * argument type. One shared function for every tab (not one per tab - the recipe itself never
     * varies, only [TabNavigationAction.route] does), applying Google's recommended tab-switch
     * recipe (`popUpTo` the graph's own start with `saveState`, `launchSingleTop`, `restoreState`)
     * so a tab bar's `onClick` doesn't need to spell it out by hand, or duplicate it, for every tab.
     */
    private fun generateTabNavigateSafetyOverload(): FunSpec =
        FunSpec.builder("navigateSafety")
            .receiver(navHostControllerType)
            .addKdoc("Switches to [action]'s tab, preserving its own back stack/scroll position.")
            .addParameter("action", tabNavigationActionType)
            .addCode(
                CodeBlock.builder()
                    .beginControlFlow("navigate(action.route)")
                    .beginControlFlow("popUpTo(graph.%M().id)", findStartDestinationMember)
                    .addStatement("saveState = true")
                    .endControlFlow()
                    .addStatement("launchSingleTop = true")
                    .addStatement("restoreState = true")
                    .endControlFlow()
                    .build(),
            )
            .build()

    /** `data object ActionTo<Graph> : TabNavigationAction(route = tabGraph)` - a typed reference to pass [generateTabNavigateSafetyOverload]'s own `navigateSafety` overload. */
    private fun generateTabAction(tabGraph: String): TypeSpec =
        TypeSpec.objectBuilder("ActionTo" + tabGraph.replaceFirstChar { it.uppercase() })
            .addModifiers(KModifier.DATA)
            .superclass(tabNavigationActionType)
            .addSuperclassConstructorParameter("route = %S", tabGraph)
            .build()

    /**
     * `NavHost(...) { moduleAGraph(navController); navigation(...) { moduleBGraph(navController); ... } }` -
     * a plain, un-tabbed [GraphManifestEntry] gets called directly, same as always; every entry
     * sharing a non-null [GraphManifestEntry.tabGraph] - however many manifests it's split across -
     * gets grouped into one shared `navigation(route = tabGraph, startDestination = ...) { }` block instead.
     */
    private fun generateBody(manifests: List<ModuleManifest>, tabStartDestinations: Map<String, String>): CodeBlock {
        val entries = manifests.flatMap { manifest -> manifest.graphs.map { Entry(manifest.packageName, it) } }
        val (tabbed, plain) = entries.partition { it.graph.tabGraph != null }
        val tabGroups = tabbed.groupBy { it.graph.tabGraph!! }

        return CodeBlock.builder()
            .beginControlFlow(
                "%M(modifier = modifier, navController = navController, startDestination = startDestination)",
                navHostMember,
            )
            .apply {
                plain.forEach { entry -> addGraphCall(entry) }
                tabGroups.forEach { (tabGraph, groupEntries) ->
                    beginControlFlow(
                        "%M(startDestination = %S, route = %S)",
                        navigationMember,
                        tabStartDestinations.getValue(tabGraph),
                        tabGraph,
                    )
                    groupEntries.forEach { entry -> addGraphCall(entry) }
                    endControlFlow()
                }
            }
            .endControlFlow()
            .build()
    }

    private fun CodeBlock.Builder.addGraphCall(entry: Entry) {
        addStatement("%M(navController)", MemberName(entry.packageName, entry.graph.graphFunctionName))
    }
}
