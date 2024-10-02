package kz.evko.processor.routeActions

import kz.evko.annotation.GenerateRouteActions

interface NavigationRouteScreens

class NavigationRouteAction(
    val fromScreen: NavigationRouteScreens,
    val toScreen: NavigationRouteScreens,
    val popUpTo: NavigationRouteScreens? = null,
    val inclusive: Boolean = false,
)

abstract class NavigationRoutesFactory {
    @GenerateRouteActions
    abstract fun getRoutes(): List<NavigationRouteAction>
}