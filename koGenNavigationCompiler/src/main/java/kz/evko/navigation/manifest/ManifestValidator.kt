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

    /** One thing eligible to become the outer `NavHost`'s own default `startDestination`. */
    private data class StartCandidate(
        val route: String,
        val isExplicit: Boolean,
        val module: String,
    )

    /**
     * The route to default the generated `AppNavHost`'s own `startDestination` parameter to.
     * Candidates are every plain (non-tab) screen's own route, plus every distinct tab's own route
     * (**never** a route that lives inside one - the outer `NavHost` can only start on something
     * directly in its own scope, which for a tab means the tab itself, not whichever screen is its
     * own `startDestination`). A tab counts as "explicit" the same way a screen does: some screen
     * in it set `startDestination = true` (`@KoGenScreen` for a plain screen, `@KoGenTab` for a
     * tab's own entry point).
     *
     * Picks the first explicit candidate (sorted by module, then declaration order within it, for
     * a deterministic result independent of manifest read order), or - if literally none was - the
     * very first candidate overall in that same order. `null` if there's nothing at all to pick
     * from.
     *
     * More than one module flagging its own preferred start destination is the *normal* case
     * (every module should be able to run/preview standalone), not an error - unlike
     * [validateNoDuplicateRoutes], there is deliberately no validation here beyond picking one of
     * them. Either way, the caller can always override the picked default explicitly.
     */
    fun resolveStartDestinationRoute(): String? {
        val ordered = allScreens.sortedBy { it.module }
        val plainCandidates = ordered.filter { it.tabGraph == null }.map {
            StartCandidate(it.screen.route, it.screen.isStartDestination, it.module)
        }
        val tabCandidates = ordered.filter { it.tabGraph != null }
            .groupBy { it.tabGraph!! }
            .map { (tabGraph, screens) ->
                StartCandidate(
                    route = tabGraph,
                    isExplicit = screens.any { it.screen.isStartDestination },
                    module = screens.minOf { it.module },
                )
            }
        val candidates = (plainCandidates + tabCandidates).sortedBy { it.module }
        return candidates.firstOrNull { it.isExplicit }?.route ?: candidates.firstOrNull()?.route
    }

    /**
     * The route to default each tab's own `startDestination` to, keyed by
     * [GraphManifestEntry.tabGraph] - the first screen (by module, then declaration order) flagged
     * `@KoGenTab(startDestination = true)`, or - if none in that tab was - its first screen
     * overall. Every distinct tab name found across [manifests] gets an entry; a tab necessarily
     * has at least one screen, since it only exists because some screen named it.
     */
    fun resolveTabStartDestinations(): Map<String, String> {
        val ordered = allScreens.sortedBy { it.module }
        return ordered.mapNotNull { it.tabGraph }.distinct().associateWith { tabGraph ->
            val screens = ordered.filter { it.tabGraph == tabGraph }
            screens.firstOrNull { it.screen.isStartDestination }?.screen?.route
                ?: screens.first().screen.route
        }
    }
}
