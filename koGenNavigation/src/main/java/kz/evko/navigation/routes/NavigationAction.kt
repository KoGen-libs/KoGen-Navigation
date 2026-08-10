package kz.evko.navigation.routes

/**
 * Implemented by every generated `<navHostName>NavigationScreens` enum - one entry per
 * `@KoGenScreen`-annotated function sharing that `navHostName`. [route] is the same route pattern
 * (e.g. `"details?id={id}"`) that entry's `composable(...)` block is registered under.
 */
interface RouteScreenType {
    val route: String
}

/**
 * Base class of every generated `ActionTo<Screen>` - a type-safe request to navigate to one
 * screen, with [route] already filled in from the action's own constructor arguments. Pass it to
 * the generated `navigateSafety` extension on `NavHostController`.
 */
open class NavigationAction(
    val route: String,
)

/**
 * Debug-log rendering of a `navigateSafety` call - the action taken, its target route's query
 * part (if any), and the `popUpTo`/`inclusive` behavior, if given.
 */
fun NavigationAction.navigationLog(
    popUpTo: RouteScreenType? = null,
    inclusive: Boolean = false,
): String = buildString {
    appendLine(" ---------------------------------------------------------------------------------")
    appendLine("| Navigation action: ${this@navigationLog.javaClass.simpleName}")
    if (route.contains("?")) {
        appendLine("| ${route.split("?").lastOrNull()}")
    }
    popUpTo?.let {
        appendLine("| popUpTo: $it")
    }
    if (inclusive) appendLine("| is inclusive")
    appendLine(" ---------------------------------------------------------------------------------")
}

/** Debug-log rendering of a `popBackSafety` call - which screen it popped from/to. */
fun navigationBackLog(
    fromScreen: String?,
    toScreen: String?,
) = buildString {
    appendLine(" ---------------------------------------------------------------------------------")
    appendLine("| Navigation back")
    appendLine("| $fromScreen -> $toScreen")
    appendLine(" ---------------------------------------------------------------------------------")
}