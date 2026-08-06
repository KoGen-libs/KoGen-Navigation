package kz.evko.navigation.contentGenerators

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import kz.evko.navigation.replaceScreenWord

class RoutesListGenerator(
    private val packageName: String,
    private val screenSuffix: String? = null,
) {
    fun generateRoutes(functionList: List<KSFunctionDeclaration>): String {
        val paramTypes: MutableMap<KSValueParameter, ArgumentTypes> = mutableMapOf()
        return buildString {
            appendLine("package $packageName\n")

            appendLine("import kz.evko.navigation.routes.NavigationAction\n")

            functionList.forEach {
                val params = it.parameters.filter { param ->
                    !param.isNavHostController() && !param.isViewModel()
                }
                val screenName = it.toString().replaceScreenWord(screenSuffix)
                if (params.isEmpty()) {
                    appendLine("data object ActionTo$screenName : NavigationAction(")
                    appendLine("\troute = \"${screenName.lowercase()}\",")
                    appendLine(")\n")
                } else {
                    appendLine("class ActionTo$screenName(")
                    params.forEach { param ->
                        val isNullable = param.type.resolve().isMarkedNullable
                        val hasDefault = param.hasDefault
                        // `param.type.toString()` is the raw, as-written type reference and can
                        // print variance markers for generic type arguments (e.g. "List<INVARIANT
                        // String>", which isn't valid Kotlin). `param.type.resolve().toString()`
                        // is the resolved type without those markers - strip its trailing "?" so
                        // we don't double it up with the nullable suffix appended below.
                        val typeName = param.type.resolve().toString().removeSuffix("?")
                        ArgumentTypes.findType(param)?.let { type ->
                            paramTypes[param] = type
                            append("\t${param.name?.asString()}: $typeName")
                            if (isNullable) append("?${if (hasDefault) " = null" else ""},") else append(",")
                        } ?: run {
                            // Fully qualify every part of the type, including generic type
                            // arguments (e.g. "kotlin.collections.List<test.app.UserProfile>") -
                            // this file only ever imports the screen function, so an unqualified
                            // custom type (or type argument) here is an unresolved reference.
                            append("\t${param.name?.asString()}: ${param.type.resolve().fullyQualifiedName()}")
                            if (isNullable) append("?${if (hasDefault) " = null" else ""},") else append(",")
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

        appendLine("\nfun NavHostController.popBackSafety(")
        appendLine("\tbackStackData: kz.evko.navigation.helpers.BackStackData<*>? = null,")
        appendLine(") {")
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
        appendLine("\t\tbackStackData?.let {")
        appendLine("\t\t\tpreviousBackStackEntry?.savedStateHandle?.set(it.data.key, it.value)")
        appendLine("\t\t}\n")
        appendLine("\t\tpopBackStack()")
        appendLine("\t}")
        appendLine("}")

        appendLine("\nfun <T> NavHostController.getResultData(")
        appendLine("\tdata: kz.evko.navigation.helpers.NavigationResultKey<T>,")
        appendLine("\tclearData: Boolean = true,")
        appendLine("): T? {")
        appendLine("\tval result = this.currentBackStackEntry?.savedStateHandle?.get(data.key) as T?")
        appendLine("\tif (clearData) this.currentBackStackEntry?.savedStateHandle?.remove<T>(data.key)")
        appendLine("\treturn result")
        appendLine("}")
    }
}