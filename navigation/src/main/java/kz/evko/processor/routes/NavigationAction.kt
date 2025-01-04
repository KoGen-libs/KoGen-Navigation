package kz.evko.processor.routes

interface RouteScreenType {
    val route: String
}

open class NavigationAction(
    val route: String,
)

fun NavigationAction.navigationLog(
    popUpTo: RouteScreenType? = null,
    inclusive: Boolean = false,
): String = buildString {
    appendLine(" ---------------------------------------------------------------------------------")
    appendLine("| Navigation action: ${this.javaClass.simpleName}")
    if (route.contains("?")) {
        appendLine("| ${route.split("?").lastOrNull()}")
    }
    popUpTo?.let {
        appendLine("| popUpTo: $it")
    }
    if (inclusive) appendLine("| is inclusive")
    appendLine(" ---------------------------------------------------------------------------------")
}

fun navigationBackLog(
    fromScreen: String?,
    toScreen: String?,
) = buildString {
    appendLine(" ---------------------------------------------------------------------------------")
    appendLine("| Navigation back")
    appendLine("| $fromScreen -> $toScreen")
    appendLine(" ---------------------------------------------------------------------------------")
}