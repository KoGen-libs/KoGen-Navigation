package kz.evko.navigation.routes

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/**
 * Base class of every generated `ActionTo<Graph>` - a typed reference to one `@KoGenTab` nested
 * graph, with [route] already filled in from its own `graph` name. Pass it to [navigateSafety]'s
 * overload below.
 *
 * Deliberately **not** related to [NavigationAction] at all (no shared supertype either way) -
 * a tab's route only makes sense through this overload's `popUpTo`/`saveState`/`launchSingleTop`/
 * `restoreState` recipe, never a plain screen's [navigateSafety] overload, and a screen's
 * [NavigationAction] the other way around - keeping them unrelated types means passing one where
 * the other belongs is a compile error, not a silent wrong navigation.
 */
open class TabNavigationAction(
    val route: String,
)

/**
 * Switches to [action]'s tab, preserving its own back stack/scroll position - Google's own
 * recommended bottom-navigation recipe (`popUpTo` the graph's own start with `saveState`, plus
 * `launchSingleTop`/`restoreState`), applied once here rather than by hand at every tab-bar
 * `onClick`, or regenerated per project the way this used to work before the Gradle plugin started
 * guaranteeing a matching runtime version - see `koGenNavigationCompiler`'s own history for why
 * that used to be necessary and no longer is.
 *
 * Overloads the same name a screen's own `navigateSafety(action: NavigationAction, ...)` uses (see
 * `koGenNavigationCompiler`'s `RoutesListGenerator.generateExtensions`) - one call to remember
 * regardless of what's being navigated to; Kotlin resolves the right one from [TabNavigationAction]
 * and `NavigationAction` being unrelated types, the same way it always resolves an overload by
 * argument type.
 */
fun NavHostController.navigateSafety(action: TabNavigationAction) {
    navigate(action.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
