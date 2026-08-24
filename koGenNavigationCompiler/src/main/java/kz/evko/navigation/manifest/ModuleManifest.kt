package kz.evko.navigation.manifest

/**
 * One screen a [BuildMode.Module] module contributes to a [GraphManifestEntry] - just enough for
 * an aggregator to validate routes and pick a `startDestination` without needing this module's
 * live KSP symbols (which it can't see across a module boundary in the first place).
 */
data class ScreenManifestEntry(
    val route: String,
    val name: String,
    /**
     * Whether this screen is its own group's `startDestination` - for a plain group
     * ([GraphManifestEntry.tabGraph] `null`), that group's own preferred app-wide default; for a
     * tab group, that tab's own entry point instead (see `@KoGenTab`) - which of the two a given
     * [GraphManifestEntry] means is entirely determined by whether it set [GraphManifestEntry.tabGraph].
     */
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
    /**
     * The tab this `navHostName` group is nested under (see `@KoGenTab`), or `null` for
     * a plain, non-nested group - unchanged, flat behavior. An aggregator (or a module resolving
     * its own tab locally - see the `shareTabGraph` KSP option) groups every [GraphManifestEntry]
     * sharing the same non-null [tabGraph] - however many modules it's split across - into one
     * `navigation(route = tabGraph, startDestination = ...) { }` block.
     */
    val tabGraph: String? = null,
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
