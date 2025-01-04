package kz.evko.processor.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class KoGenScreen(
    val startDestination: Boolean = false,
    val navHostName: String = "AppNavHost",
    val viewModelInjector: ViewModelInjector = ViewModelInjector.None,
    val animation: NavigationAnimation = NavigationAnimation.Fade,
)

enum class ViewModelInjector(private val injectorName: String, private val injectorImport: String) {
    Koin("koinViewModel", "import org.koin.androidx.compose.koinViewModel"),
    Hilt("viewModel", ""),
    None("", "");

    fun getInjectorName(type: String): String {
        return if (this == None) ""
        else {
            " = $injectorName<$type>(),"
        }
    }

    fun getInjectorImport(): String {
        return injectorImport
    }
}