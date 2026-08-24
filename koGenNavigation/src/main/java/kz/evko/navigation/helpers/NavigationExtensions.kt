package kz.evko.navigation.helpers

import android.util.Log
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.navigation.NavHostController
import kz.evko.navigation.routes.NavigationAction
import kz.evko.navigation.routes.RouteScreenType
import kz.evko.navigation.routes.navigationBackLog
import kz.evko.navigation.routes.navigationLog

/**
 * Logs the navigation, then navigates to [action]'s route.
 *
 * @param action Screen to navigate to.
 * @param popUpTo Also pop the back stack up to this destination first, if given.
 * @param inclusive Whether [popUpTo] itself is popped too, not just what's above it.
 */
public fun NavHostController.navigateSafety(
    action: NavigationAction,
    popUpTo: RouteScreenType? = null,
    inclusive: Boolean = false,
) {
    Log.d("NavigateSafety", action.navigationLog(popUpTo, inclusive))

    navigate(action.route) {
        popUpTo?.let {
            popUpTo(it.route) {
                this.inclusive = inclusive
            }
        }
    }
}

/**
 * Logs the pop, optionally stashes [backStackData] for the screen being returned to
 * (read it back there via [getResultData]), then pops the back stack.
 *
 * @param backStackData Result to hand back to the previous screen, if any.
 */
public fun NavHostController.popBackSafety(backStackData: BackStackData<*>? = null) {
    if (previousBackStackEntry != null) {
        Log.d(
            "PopBackSafety",
            navigationBackLog(
                fromScreen = currentDestination?.route?.split("?")
                    ?.firstOrNull()?.capitalize(Locale.current),
                toScreen = previousBackStackEntry?.destination?.route?.split("?")
                    ?.firstOrNull()?.capitalize(Locale.current),
            ),
        )

        backStackData?.let {
            previousBackStackEntry?.savedStateHandle?.set(it.data.key, it.value)
        }

        popBackStack()
    }
}

/**
 * Reads back a result previously stashed via [popBackSafety], or `null` if none was.
 *
 * @param data Which result slot to read.
 * @param clearData Whether to clear the stashed value after reading it.
 * @return The stashed value, or `null` if nothing was stashed.
 */
public fun <T> NavHostController.getResultData(data: NavigationResultKey<T>, clearData: Boolean = true): T? {
    val result = currentBackStackEntry?.savedStateHandle?.get(data.key) as T?
    if (clearData) currentBackStackEntry?.savedStateHandle?.remove<T>(data.key)
    return result
}
