package kz.evko.navigation.annotation

import kz.evko.navigation.helpers.NavigationAnimation

/**
 * Marks a `@Composable` function as a navigation destination for the KoGen Navigation KSP
 * compiler to pick up. Every annotated function turns into one enum entry (route), one
 * `composable(...)` block inside the generated `NavHost`, and - unless it has zero parameters
 * that need one - a typed `ActionTo<Screen>` you navigate to it with (see `navigateSafety`).
 *
 * The function's own parameters become the screen's route arguments, except two that are
 * recognized and wired up specially instead: a `NavHostController` parameter receives the
 * `NavHost`'s own controller, and a `ViewModel`-typed parameter is filled in via
 * `koinViewModel()`/`hiltViewModel()` depending on the `viewModelInjector` KSP option (a
 * `ViewModelInjector` choice, defined in the KSP compiler module) - neither is treated as a route
 * argument.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class KoGenScreen(
    /** Whether this is the graph's initial destination - its route becomes the `NavHost`'s default `startDestination`. Exactly one screen per [navHostName] group should set this. */
    val startDestination: Boolean = false,
    /** Groups screens into a `NavHost`: every screen sharing a [navHostName] is compiled into one `<navHostName>NavigationScreens` enum and one `@Composable fun <navHostName>(...)` graph. */
    val navHostName: String = "AppNavHost",
    /** Enter/exit transition applied to this screen's `composable(...)` entry. Falls back to the `defaultAnimation` KSP option (see [NavigationAnimation]) when left as [NavigationAnimation.None]. */
    val animation: NavigationAnimation = NavigationAnimation.None,
    /**
     * Deep link URI patterns for this screen, e.g. `["myapp://chat/{chatId}", "https://example.com/chat/{chatId}"]`.
     * Written out in full by hand, the same way you'd write them for `navDeepLink { uriPattern = ... }`
     * directly - a deep link's scheme/host/path is a product decision, not something derivable from
     * the screen function's signature. Each `{placeholder}` should match a parameter name on the
     * annotated function; a mismatch doesn't fail the build, but the parameter simply won't be filled
     * from that deep link at runtime.
     */
    val deepLinks: Array<String> = [],
)