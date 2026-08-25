package kz.evko.navigation.contentGenerators

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.STRING
import kz.evko.navigation.manifest.GraphManifestEntry
import kz.evko.navigation.manifest.ModuleManifest

/*
 * navigateSafety(action: TabNavigationAction) itself is NOT generated here (or anywhere) - it's a
 * real, hand-written function in koGenNavigation (kz.evko.navigation.routes.TabNavigationAction.kt),
 * same as navigateSafety(action: NavigationAction, ...)/popBackSafety/getResultData are meant to
 * be: it's pure boilerplate with no per-project types in it at all, and the Gradle plugin already
 * guarantees a matching runtime version, so there's nothing to gain by re-emitting identical text
 * into every project. Each tab screen's own `ActionTo<Screen> : TabNavigationAction` isn't built
 * here either - it's generated locally, per module, alongside every plain screen's own action (see
 * `RoutesListGenerator.generateTabRoutes`/`FileWriter.createTabRoutes`), since a tab screen's route
 * is already fully known without waiting for this aggregation step at all. This class only ever
 * builds the combined `NavHost` itself.
 */

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

        return FileSpec.builder(packageName, hostName)
            .addFunction(hostFunction)
            .build()
    }

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
