package kz.evko.processor.annotation

abstract class NavigationRouteScreens

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateScreens(
    val startDestination: Boolean = false,
    val navHostName: String = "AppNavHost",
    val viewModelInjector: ViewModelInjector = ViewModelInjector.NONE,
    val routesTo: Array<String> = [],
)

enum class ViewModelInjector(private val injectorName: String, private val injectorImport: String) {
    KOIN("koinViewModel", "import org.koin.androidx.compose.koinViewModel"),
    HILT("viewModel", ""),
    NONE("", "");

    fun getInjectorName(type: String): String {
        return if(this == NONE) ""
        else {
            " = $injectorName<$type>(),"
        }
    }

    fun getInjectorImport(): String {
        return injectorImport
    }
}