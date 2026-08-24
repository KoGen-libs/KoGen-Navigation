package kz.evko.navigation.routes

/**
 * Base class of every generated `ActionTo<Graph>` - a typed reference to one `@KoGenTab` nested
 * graph, with [route] already filled in from its own `graph` name. Pass it to the generated
 * `navigateToTab` extension on `NavHostController`.
 *
 * Deliberately **not** related to [NavigationAction] at all (no shared supertype either way) -
 * a tab's route only makes sense through the tab-switch `popUpTo`/`saveState`/`launchSingleTop`/
 * `restoreState` recipe `navigateToTab` applies, never a plain `navigateSafety` call, and a
 * screen's [NavigationAction] the other way around - keeping them unrelated types means passing
 * one where the other belongs is a compile error, not a silent wrong navigation.
 */
open class TabNavigationAction(
    val route: String,
)
