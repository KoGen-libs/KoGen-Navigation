package kz.evko.navigation.annotation

import kz.evko.navigation.helpers.NavigationAnimation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class KoGenScreen(
    val startDestination: Boolean = false,
    val navHostName: String = "AppNavHost",
    val animation: NavigationAnimation = NavigationAnimation.None,
)