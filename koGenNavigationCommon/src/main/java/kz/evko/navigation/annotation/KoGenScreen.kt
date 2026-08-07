package kz.evko.navigation.annotation

import kz.evko.navigation.helpers.NavigationAnimation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class KoGenScreen(
    val startDestination: Boolean = false,
    val navHostName: String = "AppNavHost",
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