package kz.evko.processor.contentGenerators

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import kz.evko.processor.annotation.KoGenScreen
import kz.evko.processor.annotation.NavigationAnimation
import kz.evko.processor.annotation.ViewModelInjector
import kz.evko.processor.annotationParameterByName
import kz.evko.processor.replaceScreenWord

internal class NavHostContentGenerator(
    private val packageName: String,
) {
    fun generateNavHost(functionList: List<KSFunctionDeclaration>, hostName: String): String {
        return buildString {
            appendLine("package $packageName\n")

            append(generateImports(functionList))

            append(generateNavHostFunction(functionList, hostName))

            functionList.forEach {
                append(
                    "\t\tcomposable("
                )
                val params = it.parameters.filter { parameter ->
                    !parameter.isNavHostController() && !parameter.isViewModel()
                }
                val animation = it.getAnimationType().type.buildAnimationContent()
                if (params.isEmpty()) {
                    appendLine(
                        "\n\t\t\troute = ${hostName}NavigationScreens.${
                            it.toString().replaceScreenWord()
                        }.route,"
                    )
                    append(animation)
                    appendLine("\t\t) {")
                } else {
                    appendLine(
                        "\n\t\t\troute = ${hostName}NavigationScreens.${
                            it.toString().replaceScreenWord()
                        }.route,"
                    )
                    appendLine("\t\t\targuments = listOf(")
                    params.forEach { parameter ->
                        appendLine(ArgumentTypes.getNavArgsString(parameter))
                    }
                    appendLine("\t\t\t),")
                    append(animation)
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
                if (it.key != ViewModelInjector.None) {
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
                KoGenScreen::class, "startDestination"
            )
        } ?: functionList.first()
        val startDestinationName = startDestination.toString().replaceScreenWord()

        appendLine("@Composable")
        appendLine("fun $hostName(")
        appendLine("\tmodifier: Modifier = Modifier,")
        appendLine("\tnavController: NavHostController,")
        appendLine("\tstartDestination: String = ${hostName}NavigationScreens.$startDestinationName.route")
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

                    if (viewModelInjector != ViewModelInjector.None) {
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
        KoGenScreen::class,
        viewModelInjectorName,
    )
    return ViewModelInjector.entries.firstOrNull {
        it.name == injectorType
    } ?: ViewModelInjector.None
}

fun KSFunctionDeclaration.getAnimationType(): NavigationAnimation {
    val animationName = "animation"
    val animationType = this.annotationParameterByName(
        KoGenScreen::class,
        animationName,
    )
    return NavigationAnimation.entries.firstOrNull {
        it.name == animationType
    } ?: NavigationAnimation.Fade
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