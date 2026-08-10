package kz.evko.navigation.manifest

/**
 * One screen a [BuildMode.Module] module contributes to a [GraphManifestEntry] - just enough for
 * an aggregator to validate routes and pick a `startDestination` without needing this module's
 * live KSP symbols (which it can't see across a module boundary in the first place).
 */
data class ScreenManifestEntry(
    val route: String,
    val name: String,
    val isStartDestination: Boolean,
)

/**
 * One `fun NavGraphBuilder.<navHostName>Graph(navController)` a module generated - enough for an
 * aggregator to call it (by [graphFunctionName], qualified with [ModuleManifest.packageName]) and
 * to validate the screens it contributes.
 */
data class GraphManifestEntry(
    val graphFunctionName: String,
    val screens: List<ScreenManifestEntry>,
)

/**
 * What a [kz.evko.navigation.helpers.BuildMode.Module] module writes about itself - see
 * `FileWriter.createManifest` for where/how it's written, and `ManifestReader` for how an
 * aggregator reads it back in from a directory of these. Serialized as plain JSON via Gson.
 */
data class ModuleManifest(
    val module: String,
    val packageName: String,
    val graphs: List<GraphManifestEntry>,
)
