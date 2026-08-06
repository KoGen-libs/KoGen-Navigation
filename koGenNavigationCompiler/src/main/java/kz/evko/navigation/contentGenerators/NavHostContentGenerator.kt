package kz.evko.navigation.contentGenerators

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import kz.evko.navigation.annotation.KoGenScreen
import kz.evko.navigation.helpers.NavigationAnimation
import kz.evko.navigation.helpers.ViewModelInjector
import kz.evko.navigation.annotationParameterByName
import kz.evko.navigation.booleanAnnotationParameterByName
import kz.evko.navigation.replaceScreenWord

internal class NavHostContentGenerator(
    private val packageName: String,
    private val screenSuffix: String?,
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
                            it.toString().replaceScreenWord(screenSuffix)
                        }.route,"
                    )
                    append(animation)
                    appendLine("\t\t) {")
                } else {
                    appendLine(
                        "\n\t\t\troute = ${hostName}NavigationScreens.${
                            it.toString().replaceScreenWord(screenSuffix)
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

                // The generated call is `koinViewModel<MyViewModel>()`/`viewModel<MyViewModel>()`
                // - the ViewModel's own type needs importing too, just like the screen function
                // above. Without this, it's an unresolved reference whenever the ViewModel lives
                // in a different package than this generated file (i.e. always, by design - see
                // the packageName ".navigation" suffix).
                functionList
                    .flatMap { it.parameters }
                    .filter { it.isViewModel() }
                    .mapNotNull { it.type.resolve().declaration.qualifiedName?.asString() }
                    .distinct()
                    .forEach { appendLine("import $it") }
            }

            append("\n")
        }
    }

    private fun generateNavHostFunction(
        functionList: List<KSFunctionDeclaration>,
        hostName: String,
    ) = buildString {
        val startDestination = functionList.firstOrNull {
            it.booleanAnnotationParameterByName(
                KoGenScreen::class, "startDestination"
            )
        } ?: functionList.first()
        val startDestinationName = startDestination.toString().replaceScreenWord(screenSuffix)

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

private const val VIEW_MODEL_QUALIFIED_NAME = "androidx.lifecycle.ViewModel"

/**
 * True if this parameter's type is (or extends, however deep) `androidx.lifecycle.ViewModel` -
 * checked via the resolved type hierarchy, not by guessing from how the default value is
 * obtained. That used to be detected by string-matching the parameter's default value expression
 * against the literal text "viewModel" - which silently missed anything obtained any other way
 * (`hiltViewModel()`, `koinViewModel()` called directly, a custom factory function, or no default
 * value at all) and quietly treated it as a regular route parameter instead.
 */
fun KSValueParameter.isViewModel(): Boolean {
    val declaration = type.resolve().declaration as? KSClassDeclaration ?: return false
    if (declaration.qualifiedName?.asString() == VIEW_MODEL_QUALIFIED_NAME) return true
    return declaration.getAllSuperTypes().any {
        it.declaration.qualifiedName?.asString() == VIEW_MODEL_QUALIFIED_NAME
    }
}