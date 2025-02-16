package kz.evko.navigation.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class KoGenScreen(
    val startDestination: Boolean = false,
    val navHostName: String = "AppNavHost",
    val animation: NavigationAnimation = NavigationAnimation.None,
)