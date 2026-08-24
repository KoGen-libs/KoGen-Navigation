package kz.evko.navigation.annotation

import kz.evko.navigation.helpers.NavigationAnimation

/**
 * Marks a `@Composable` function as one screen inside a nested tab graph - used **instead of**
 * `@KoGenScreen`, never alongside it. Every screen sharing a [graph] is compiled into one
 * `NavGraphBuilder` extension, the same way a `navHostName` group would be, but wrapped in a real
 * `navigation(route = graph, startDestination = ...) { }` block inside one shared `NavHost` -
 * Google's recommended bottom-navigation pattern (one back stack per tab; `popUpTo`/`saveState`/
 * `restoreState` at the tab-bar call site work as intended) - instead of either flatly merging the
 * tab's screens (no per-tab back stack at all) or generating it as its own separate `NavHost` (no
 * shared back stack/state at the top level).
 *
 * [startDestination] means only "this screen is [graph]'s own entry point" - **never** the whole
 * app's own default. A screen living inside a nested graph can't be set as the *outer* `NavHost`'s
 * `startDestination` directly - that has to be the tab's own route. When this tab ends up being
 * picked as the app's default landing spot (see `ManifestValidator.resolveStartDestinationRoute`),
 * the outer `NavHost` starts on [graph] itself, never on whichever screen is flagged here.
 *
 * Only one screen per tab needs to set [startDestination] - setting it on more than one is a
 * mistake, not a build failure: the first one found (by declaration order, then module order
 * across a multi-module build) wins, the rest are reported as a KSP warning. A helper library
 * should never crash a consumer's build over a preference conflict it can resolve deterministically
 * on its own.
 *
 * A typed `fun NavHostController.navigateTo<Graph>()` is also generated for [graph] - applying
 * that same `popUpTo`/`saveState`/`launchSingleTop`/`restoreState` recipe by hand every time a tab
 * bar switches tabs would be easy to get subtly wrong.
 *
 * Whether this tab is wrapped locally or deferred to a `BuildMode.Aggregator` (when it spans more
 * than one `BuildMode.Module` module) is controlled by the `shareTabGraph` KSP option / Gradle DSL
 * property, not by anything here.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class KoGenTab(
    /** Groups screens into a tab: every screen sharing a [graph] is nested together under this route. */
    val graph: String,
    /** Whether this screen is [graph]'s own `startDestination` - never the whole app's own. */
    val startDestination: Boolean = false,
    /** Enter/exit transition applied to this screen's `composable(...)` entry. Same as `@KoGenScreen.animation`. */
    val animation: NavigationAnimation = NavigationAnimation.None,
    /** Deep link URI patterns for this screen. Same as `@KoGenScreen.deepLinks` - see its own doc for the exact shape. */
    val deepLinks: Array<String> = [],
)
