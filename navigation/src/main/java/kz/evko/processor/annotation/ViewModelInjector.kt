package kz.evko.processor.annotation

enum class ViewModelInjector(
    val diName: String,
    private val injectorName: String,
    private val injectorImport: String
) {
    Koin("koin", "koinViewModel", "import org.koin.androidx.compose.koinViewModel"),
    Hilt("hilt", "viewModel", ""),
    None("", "", "");

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