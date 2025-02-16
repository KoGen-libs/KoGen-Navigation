package kz.evko.navigation.contentGenerators

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import kz.evko.navigation.annotation.KoGenScreen
import kz.evko.navigation.annotation.NavigationAnimation
import kz.evko.navigation.annotation.ViewModelInjector
import kz.evko.navigation.annotationParameterByName
import kz.evko.navigation.replaceScreenWord

internal class NavHostContentGenerator(
    private val packageName: String,
) {
    fun generateNavHost(
        functionList: List<KSFunctionDeclaration>,
        hostName: String,
        viewModelInjector: ViewModelInjector,
        defaultAnimation: NavigationAnimation,
    ): String {
        return buildString {
            appendLine("package $packageName\n")

            append(generateImports(functionList, viewModelInjector))

            append(generateNavHostFunction(functionList, hostName))

            functionList.forEach {
                append(
                    "\t\tcomposable("
                )
                val params = it.parameters.filter { parameter ->
                    !parameter.isNavHostController() && !parameter.isViewModel()
                }
                val animation = it.getAnimationType(defaultAnimation).type.buildAnimationContent()
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

                    append(generateScreenParameters(it, viewModelInjector))

                    appendLine("\t\t\t)")
                }
                appendLine("\t\t}")
            }

            appendLine("\t}")
            appendLine("}")
        }
    }

    private fun generateImports(
        functionList: List<KSFunctionDeclaration>,
        viewModelInjector: ViewModelInjector,
    ): String {
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
            }

            if (viewModelInjector != ViewModelInjector.None) {
                appendLine(viewModelInjector.getInjectorImport())
            }

            append("\n")
        }
    }

    private fun generateNavHostFunction(
        functionList: List<KSFunctionDeclaration>,
        hostName: String,
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

    private fun generateScreenParameters(
        function: KSFunctionDeclaration,
        viewModelInjector: ViewModelInjector,
    ) = buildString {
        function.parameters.forEach { parameter ->
            when {
                parameter.isNavHostController() ->
                    appendLine("\t\t\t\t${parameter.name?.asString()} = navController,")

                parameter.isViewModel() -> {
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

fun KSFunctionDeclaration.getAnimationType(defaultAnimation: NavigationAnimation): NavigationAnimation {
    val animationName = "animation"
    val animationType = this.annotationParameterByName(
        KoGenScreen::class,
        animationName,
    )
    val screenAnimationType = NavigationAnimation.entries.firstOrNull {
        it.name == animationType
    } ?: NavigationAnimation.None

    return if (screenAnimationType == NavigationAnimation.None) {
        defaultAnimation
    } else {
        screenAnimationType
    }
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