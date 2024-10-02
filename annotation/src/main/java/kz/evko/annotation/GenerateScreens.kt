package kz.evko.annotation


@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateScreens(
    val startDestination: Boolean = false,
    val navHostName: String = "AppNavHost",
)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateRouteActions