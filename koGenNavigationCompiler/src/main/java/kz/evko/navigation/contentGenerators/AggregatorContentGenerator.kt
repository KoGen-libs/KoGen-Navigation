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

/*
 * navigateSafety(action: TabNavigationAction) itself is NOT generated here (or anywhere) - it's a
 * real, hand-written function in koGenNavigation (kz.evko.navigation.routes.TabNavigationAction.kt),
 * same as navigateSafety(action: NavigationAction, ...)/popBackSafety/getResultData are meant to
 * be: it's pure boilerplate with no per-project types in it at all, and the Gradle plugin already
 * guarantees a matching runtime version, so there's nothing to gain by re-emitting identical text
 * into every project - only [generateTabAction] (which needs each tab's own route) has any actual
 * per-project information to contribute.
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
        tabStartDestinations.keys.forEach { tabGraph -> fileBuilder.addType(generateTabAction(tabGraph)) }
        return fileBuilder.build()
    }

    /** `data object ActionTo<Graph> : TabNavigationAction(route = tabGraph)` - a typed reference to pass to the runtime's `navigateSafety(action: TabNavigationAction)` overload. */
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
