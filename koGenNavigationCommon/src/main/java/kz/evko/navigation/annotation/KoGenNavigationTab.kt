package kz.evko.navigation.annotation

/**
 * Marks a `@KoGenScreen` function's `navHostName` group as a nested tab graph - wrapped in its own
 * `navigation(route = [graph], startDestination = ...) { }` block inside one shared `NavHost`,
 * Google's recommended bottom-navigation pattern (one back stack per tab; `popUpTo`/`saveState`/
 * `restoreState` at the tab-bar call site work as intended) - instead of either flatly merging its
 * screens into the shared graph (no per-tab back stack at all) or generating it as its own,
 * separate `NavHost` (no shared back stack/state at the top level).
 *
 * A `navHostName` group is generated as a single unit regardless of how many of its screens carry
 * this annotation, so only one needs to - typically its `startDestination = true` one. Annotating
 * more than one with a different [graph] (or setting [startDestination] on more than one) is a
 * mistake, not a build failure: the first one found (by declaration order, then module order
 * across a multi-module build) wins, the rest are reported as a KSP warning - a helper library
 * should never crash a consumer's build over a preference conflict it can resolve deterministically
 * on its own. Leaving every screen in a `navHostName` group without this annotation keeps that
 * group exactly as it always was - a plain, non-nested graph - so adopting tabs is entirely opt-in.
 *
 * Whether this tab is wrapped locally or deferred to a `BuildMode.Aggregator` (when it spans more
 * than one `BuildMode.Module` module) is controlled by the `shareTabGraph` KSP option / Gradle DSL
 * property, not by anything here.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class KoGenNavigationTab(
    /** This tab's own route in the shared `NavHost` - what a tab bar's `navController.navigate(...)` targets. */
    val graph: String,
    /** Whether this screen is [graph]'s own `startDestination`. Exactly one screen per tab should set this. */
    val startDestination: Boolean = false,
)
