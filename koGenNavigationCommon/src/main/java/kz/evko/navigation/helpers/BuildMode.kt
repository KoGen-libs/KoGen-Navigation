package kz.evko.navigation.helpers

/**
 * How the KSP compiler should treat this module's `@KoGenScreen` functions - the `buildMode`
 * KSP option (or the `koGenNavigation { buildMode = ... }` Gradle DSL).
 */
enum class BuildMode(val argName: String) {
    /**
     * Today's behavior, unchanged: every screen sharing a `navHostName` gets a self-contained
     * `@Composable fun <navHostName>(navController, modifier, startDestination) { NavHost(...) { ... } }`.
     * The right choice for an app that isn't split into feature modules.
     */
    Single("single"),

    /**
     * For a feature module that will be combined into a larger app by an [Aggregator] module.
     * Instead of a self-contained `NavHost`, generates a `fun NavGraphBuilder.<navHostName>Graph(navController)`
     * - a fragment meant to be called inside *someone else's* `NavHost { }` block, so every
     * module's screens end up sharing one graph/back stack. Also writes a small manifest
     * describing this module's screens for the aggregator to pick up - see the KDoc on the
     * content generators for its exact shape.
     *
     * Doesn't generate `NavigationExtensions.kt` (`navigateSafety`/`popBackSafety`/`getResultData`)
     * - those are context-free boilerplate that only needs to exist once, reachable from
     * wherever the app actually is; generating it per module would produce colliding top-level
     * functions once multiple modules end up on the same app's classpath. [Aggregator] generates
     * it instead.
     */
    Module("module"),

    /**
     * For the module (typically the real Android application module) that combines every
     * [Module]-mode feature module it depends on into one graph: reads their manifests, validates
     * their routes don't collide, and generates one
     * `@Composable fun AppNavHost(navController, modifier, startDestination) { NavHost(...) { moduleAGraph(navController); moduleBGraph(navController); ... } }`.
     * Also generates `NavigationExtensions.kt` (only [Module] skips it) and, same as [Single],
     * still processes any `@KoGenScreen` functions of its own, if it has any.
     */
    Aggregator("aggregator"),
}
