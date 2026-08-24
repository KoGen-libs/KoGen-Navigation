package kz.evko.navigation.manifest

import com.google.devtools.ksp.processing.KSPLogger

/**
 * Validates the combined set of [manifests] an aggregator (`BuildMode.Aggregator`) collects
 * across every `BuildMode.Module` module it depends on - mirrors how `koGenDi-compiler`'s own
 * `DependencyValidator` catches a graph-wide problem at compile time instead of a confusing
 * runtime one.
 */
class ManifestValidator(
    private val manifests: List<ModuleManifest>,
    private val logger: KSPLogger,
) {
    private data class ScreenLocation(
        val module: String,
        val screen: ScreenManifestEntry,
        val tabGraph: String?,
    )

    private val allScreens: List<ScreenLocation> = manifests.flatMap { manifest ->
        manifest.graphs.flatMap { graph ->
            graph.screens.map { screen -> ScreenLocation(manifest.module, screen, graph.tabGraph) }
        }
    }

    /**
     * Reports (via [logger], failing the aggregator's own compilation) every route more than one
     * screen registers - across *every* manifest combined, not just within one module, since
     * that's exactly the kind of collision a module can't catch on its own. No inline source
     * location is possible here (these screens live in other modules' already-finished
     * compilations, not this one's), so the message names the colliding modules/screens by name
     * instead.
     */
    fun validateNoDuplicateRoutes() {
        allScreens.groupBy { it.screen.route }
            .filterValues { it.size > 1 }
            .forEach { (route, locations) ->
                logger.error(
                    "Duplicate route \"$route\": " +
                        locations.joinToString(" and ") { "${it.screen.name} in module ${it.module}" },
                )
            }
    }

    /**
     * The route to default the generated `AppNavHost`'s own `startDestination` parameter to - the
     * first screen (sorted by module name, then declaration order within it, for a deterministic
     * result independent of manifest read order) flagged `@KoGenScreen(startDestination = true)`,
     * or - if literally none was - the very first screen overall in that same order. `null` if
     * there are no screens at all to pick from.
     *
     * More than one module flagging its own preferred start destination is the *normal* case
     * (every module should be able to run/preview standalone), not an error - unlike
     * [validateNoDuplicateRoutes], there is deliberately no validation here beyond picking one of
     * them. Either way, the caller can always override the picked default explicitly.
     */
    fun resolveStartDestinationRoute(): String? {
        val ordered = allScreens.sortedBy { it.module }
        return ordered.firstOrNull { it.screen.isStartDestination }?.screen?.route
            ?: ordered.firstOrNull()?.screen?.route
    }

    /**
     * The route to default each tab graph's own `startDestination` to, keyed by
     * [kz.evko.navigation.manifest.GraphManifestEntry.tabGraph] - same resolution as
     * [resolveStartDestinationRoute], just scoped to each tab's own screens instead of the whole
     * app: the first one (by module, then declaration order) flagged
     * `@KoGenNavigationTab(startDestination = true)`, or - if none in that tab was - its first
     * screen overall. Every distinct tab name found across [manifests] gets an entry; a tab
     * necessarily has at least one screen, since it only exists because some screen named it.
     */
    fun resolveTabStartDestinations(): Map<String, String> {
        val ordered = allScreens.sortedBy { it.module }
        return ordered.mapNotNull { it.tabGraph }.distinct().associateWith { tabGraph ->
            val screens = ordered.filter { it.tabGraph == tabGraph }
            screens.firstOrNull { it.screen.isTabStartDestination }?.screen?.route
                ?: screens.first().screen.route
        }
    }
}
