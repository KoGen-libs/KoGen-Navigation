package kz.evko.navigation.contentGenerators

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import kz.evko.navigation.replaceScreenWord

class RoutesListGenerator(
    private val packageName: String,
) {
    fun generateRoutes(functionList: List<KSFunctionDeclaration>): String {
        var paramTypes: MutableMap<KSValueParameter, ArgumentTypes> = mutableMapOf()
        return buildString {
            appendLine("package $packageName\n")

            appendLine("import kz.evko.navigation.routes.NavigationAction\n")

            functionList.forEach {
                val params = it.parameters.filter { param ->
                    !param.isNavHostController() && !param.isViewModel()
                }
                val screenName = it.toString().replaceScreenWord()
                if (params.isEmpty()) {
                    appendLine("data object ActionTo$screenName : NavigationAction(")
                    appendLine("\troute = \"${screenName.lowercase()}\",")
                    appendLine(")\n")
                } else {
                    appendLine("class ActionTo$screenName(")
                    params.forEach { param ->
                        ArgumentTypes.findType(param)?.let { type ->
                            paramTypes[param] = type
                            appendLine("\t${param.name?.asString()}: ${param.type},")
                        } ?: run {
                            appendLine("\t${param.name?.asString()}: ${param.type.resolve().declaration.packageName.asString()}.${param.type},")
                        }
                    }
                    appendLine("): NavigationAction(")
                    appendLine("\troute = \"${
                        params.joinToString(
                            separator = "&",
                            prefix = "${screenName.lowercase()}?",
                            postfix = "",
                        ) { param ->
                            paramTypes[param]?.let {
                                "$param=\$$param"
                            } ?: run {
                                "$param=\${com.google.gson.Gson().toJson($param)}"
                            }
                            
                        }
                    }\","
                    )
                    appendLine(")\n")
                }
            }
        }
    }

    fun generateExtensions(): String = buildString {
        appendLine("package $packageName\n")

        appendLine("import android.util.Log")
        appendLine("import androidx.compose.ui.text.capitalize")
        appendLine("import androidx.compose.ui.text.intl.Locale")
        appendLine("import androidx.navigation.NavHostController")
        appendLine("import kz.evko.navigation.routes.navigationLog\n")

        appendLine("fun NavHostController.navigateSafety(")
        appendLine("\taction: kz.evko.navigation.routes.NavigationAction,")
        appendLine("\tpopUpTo: kz.evko.navigation.routes.RouteScreenType? = null,")
        appendLine("\tinclusive: Boolean = false,")
        appendLine(") {")
        appendLine("\tLog.d(\"NavigateSafety\", action.navigationLog(popUpTo, inclusive))\n")

        appendLine("\tnavigate(action.route) {")
        appendLine("\t\tpopUpTo?.let {")
        appendLine("\t\t\tpopUpTo(it.route) {")
        appendLine("\t\t\t\tthis.inclusive = inclusive")
        appendLine("\t\t\t}")
        appendLine("\t\t}")
        appendLine("\t}")
        appendLine("}")

        appendLine("fun NavHostController.popBackSafety() {")
        appendLine("\tif (previousBackStackEntry != null) {")
        appendLine("\t\tLog.d(")
        appendLine("\t\t\t\"PopBackSafety\",")
        appendLine("\t\t\tkz.evko.navigation.routes.navigationBackLog(")
        appendLine("\t\t\t\tfromScreen = this@popBackSafety.currentDestination?.route?.split(\"?\")")
        appendLine("\t\t\t\t\t?.firstOrNull()?.capitalize(Locale.current),")
        appendLine("\t\t\t\ttoScreen = this@popBackSafety.previousBackStackEntry?.destination?.route?.split(\"?\")")
        appendLine("\t\t\t\t\t?.firstOrNull()?.capitalize(Locale.current)")
        appendLine("\t\t\t)")
        appendLine("\t\t)\n")
        appendLine("\t\tpopBackStack()")
        appendLine("\t}")
        appendLine("}")
    }
}