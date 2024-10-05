package kz.evko.processor.contentGenerators

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import kz.evko.processor.annotation.GenerateScreens
import kz.evko.processor.annotation.ViewModelInjector
import kz.evko.processor.annotationParameterByName
import kz.evko.processor.kspPackage
import kz.evko.processor.replaceScreenWord

class NavHostContentGenerator {
    fun generateContent(functionList: List<KSFunctionDeclaration>, hostName: String): String {
        return generateTexts(functionList, hostName)
    }

    private fun generateTexts(functionList: List<KSFunctionDeclaration>, hostName: String) =
        buildString {
            appendLine("package ${kspPackage()}\n")

            append(generateImports(functionList))

            append(generateNavHostFunction(functionList, hostName))

            functionList.forEach {
                append(
                    "\t\tcomposable("
                )
                val params = it.parameters.filter { parameter ->
                    !parameter.isNavHostController() && !parameter.isViewModel()
                }
                if (params.isEmpty()) {
                    append(
                        "${hostName}NavigationScreens.${
                            it.toString().replaceScreenWord()
                        }.name) {\n"
                    )
                } else {
                    appendLine(
                        "\n\t\t\t${hostName}NavigationScreens.${
                            it.toString().replaceScreenWord()
                        }.name,"
                    )
                    appendLine("\t\t\targuments = listOf(")
                    params.forEach { parameter ->
                        appendLine(ArgumentTypes.getNavArgsString(parameter))
                    }
                    appendLine("\t\t\t)")

                    appendLine("\t\t) {")
                }

                if (it.parameters.isEmpty()) {
                    appendLine("\t\t\t${it.simpleName.asString()}()")
                } else {
                    appendLine("\t\t\t${it.simpleName.asString()}(")

                    append(generateScreenParameters(it))

                    appendLine("\t\t\t)")
                }
                appendLine("\t\t}")
            }

            appendLine("\t}")
            appendLine("}")
        }

    private fun generateImports(functionList: List<KSFunctionDeclaration>): String {
        val injectors: MutableMap<ViewModelInjector, String> = mutableMapOf()

        return buildString {
            appendLine("import androidx.compose.runtime.Composable")
            appendLine("import androidx.compose.ui.Modifier")
            appendLine("import androidx.navigation.NavHostController")
            appendLine("import androidx.navigation.NavType")
            appendLine("import androidx.navigation.compose.NavHost")
            appendLine("import androidx.navigation.compose.composable")
            appendLine("import androidx.navigation.navArgument")
            appendLine("import com.google.gson.Gson")

            functionList.forEach {
                appendLine("import ${it.packageName.asString()}.${it.simpleName.asString()}")
                val type = it.getViewModelInjectorType()
                injectors[type] = type.getInjectorImport()
            }

            injectors.forEach {
                if (it.key != ViewModelInjector.NONE) {
                    appendLine(it.value)
                }
            }

            append("\n")
        }
    }

    private fun generateNavHostFunction(
        functionList: List<KSFunctionDeclaration>,
        hostName: String
    ) = buildString {
        val startDestination = functionList.firstOrNull {
            it.annotationParameterByName<Boolean>(
                GenerateScreens::class, "startDestination"
            )
        } ?: functionList.first()
        val startDestinationName = startDestination.toString().replaceScreenWord()

        appendLine("@Composable")
        appendLine("fun $hostName(")
        appendLine("\tmodifier: Modifier = Modifier,")
        appendLine("\tnavController: NavHostController,")
        appendLine("\tstartDestination: String = ${hostName}NavigationScreens.$startDestinationName.name")
        appendLine(") {")

        appendLine("\tNavHost(")
        appendLine("\t\tmodifier = modifier,")
        appendLine("\t\tnavController = navController,")
        appendLine("\t\tstartDestination = startDestination")
        appendLine("\t) {")
    }

    private fun generateScreenParameters(function: KSFunctionDeclaration) = buildString {
        function.parameters.forEach { parameter ->
            when {
                parameter.isNavHostController() ->
                    appendLine("\t\t\t\t${parameter.name?.asString()} = navController,")

                parameter.isViewModel() -> {
                    val viewModelInjector = function.getViewModelInjectorType()

                    if (viewModelInjector != ViewModelInjector.NONE) {
                        appendLine(
                            "\t\t\t\t${parameter.name?.asString()}${
                                viewModelInjector.getInjectorName(
                                    parameter.type.toString()
                                )
                            }"
                        )
                    }
                }

                else ->
                    appendLine(
                        "\t\t\t\t${parameter.name?.asString()} = ${
                            ArgumentTypes.getArgumentString(
                                parameter
                            )
                        },"
                    )
            }
        }
    }
}

fun KSFunctionDeclaration.getViewModelInjectorType(): ViewModelInjector {
    val viewModelInjectorName = "viewModelInjector"
    val injectorType = this.annotationParameterByName(
        GenerateScreens::class,
        viewModelInjectorName,
    )
    return ViewModelInjector.entries.firstOrNull {
        it.name == injectorType
    } ?: ViewModelInjector.NONE
}

fun KSValueParameter.isNavHostController(): Boolean {
    return type.toString() == "NavHostController"
}

fun KSValueParameter.isViewModel(): Boolean {
    var currentParent = type.parent
    do {
        if (currentParent.toString() == "viewModel") {
            return true
        } else {
            currentParent = currentParent?.parent
        }
    } while (currentParent != null)
    return false
}