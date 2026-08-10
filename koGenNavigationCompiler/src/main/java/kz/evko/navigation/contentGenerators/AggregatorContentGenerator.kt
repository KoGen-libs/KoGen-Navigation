package kz.evko.navigation.contentGenerators

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.STRING
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

    /**
     * @param startDestinationRoute The route to default the generated function's own
     *   `startDestination` parameter to (see `ManifestValidator.resolveStartDestinationRoute`) -
     *   `null` makes it a required parameter instead, since there's nothing to default to.
     */
    fun generateAppNavHost(
        manifests: List<ModuleManifest>,
        hostName: String,
        startDestinationRoute: String?,
    ): FileSpec {
        val startDestinationParam = ParameterSpec.builder("startDestination", STRING).apply {
            if (startDestinationRoute != null) defaultValue("%S", startDestinationRoute)
        }.build()

        val hostFunction = FunSpec.builder(hostName)
            .addAnnotation(composableAnnotation)
            .addParameter(ParameterSpec.builder("modifier", modifierType).defaultValue("%T", modifierType).build())
            .addParameter("navController", navHostControllerType)
            .addParameter(startDestinationParam)
            .addCode(generateBody(manifests))
            .build()

        return FileSpec.builder(packageName, hostName)
            .addFunction(hostFunction)
            .build()
    }

    /** `NavHost(...) { moduleAGraph(navController); moduleBGraph(navController); ... }` - one call per manifest's graph(s). */
    private fun generateBody(manifests: List<ModuleManifest>): CodeBlock = CodeBlock.builder()
        .beginControlFlow(
            "%M(modifier = modifier, navController = navController, startDestination = startDestination)",
            navHostMember,
        )
        .apply {
            manifests.forEach { manifest ->
                manifest.graphs.forEach { graph ->
                    addStatement("%M(navController)", MemberName(manifest.packageName, graph.graphFunctionName))
                }
            }
        }
        .endControlFlow()
        .build()
}
