package kz.evko.navigation.contentGenerators

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.ksp.toTypeName
import kz.evko.navigation.annotation.KoGenScreen
import kz.evko.navigation.annotationParameterByName
import kz.evko.navigation.booleanAnnotationParameterByName
import kz.evko.navigation.helpers.NavigationAnimation
import kz.evko.navigation.helpers.ViewModelInjector
import kz.evko.navigation.replaceScreenWord
import kz.evko.navigation.stringListAnnotationParameterByName

internal class NavHostContentGenerator(
    private val packageName: String,
    private val screenSuffix: String?,
    private val logger: KSPLogger,
) {
    private val composableAnnotation = ClassName("androidx.compose.runtime", "Composable")
    private val modifierType = ClassName("androidx.compose.ui", "Modifier")
    private val navHostControllerType = ClassName("androidx.navigation", "NavHostController")
    private val navHostMember = MemberName("androidx.navigation.compose", "NavHost")
    private val composableMember = MemberName("androidx.navigation.compose", "composable")
    private val navDeepLinkMember = MemberName("androidx.navigation", "navDeepLink")
    private val deepLinkPlaceholder = Regex("\\{(\\w+)}")

    fun generateNavHost(
        functionList: List<KSFunctionDeclaration>,
        hostName: String,
        viewModelInjector: ViewModelInjector,
        defaultAnimation: NavigationAnimation,
    ): FileSpec {
        val screensEnum = ClassName(packageName, "${hostName}NavigationScreens")
        val startDestination = functionList.firstOrNull {
            it.booleanAnnotationParameterByName(KoGenScreen::class, "startDestination")
        } ?: functionList.first()
        val startDestinationName = startDestination.toString().replaceScreenWord(screenSuffix)

        val hostFunction = FunSpec.builder(hostName)
            .addAnnotation(composableAnnotation)
            .addParameter(ParameterSpec.builder("modifier", modifierType).defaultValue("%T", modifierType).build())
            .addParameter("navController", navHostControllerType)
            .addParameter(
                ParameterSpec.builder("startDestination", STRING)
                    .defaultValue("%T.%L.route", screensEnum, startDestinationName)
                    .build(),
            )
            .addCode(generateNavHostBody(functionList, hostName, viewModelInjector, defaultAnimation))
            .build()

        // ArgumentTypes'/AnimationType's generated fragments still reference these three by their
        // bare/simple names as plain text (not through a %T/%M placeholder KotlinPoet can see), so
        // they still need a manual, always-present import. The ViewModel injector function *and*
        // the ViewModel type it's parameterized with, on the other hand, are both referenced via
        // %M/%T below (see generateScreenParameters) - KotlinPoet auto-imports both from actual
        // usage, so there's nothing to hand-collect for them here at all. That's exactly the class
        // of bug this used to have (Hilt's injector function had no import; the ViewModel type
        // argument's import was missing for every injector).
        return FileSpec.builder(packageName, hostName)
            .addImport("androidx.navigation", "NavType")
            .addImport("androidx.navigation", "navArgument")
            .addImport("com.google.gson", "Gson")
            .addFunction(hostFunction)
            .build()
    }

    private fun generateNavHostBody(
        functionList: List<KSFunctionDeclaration>,
        hostName: String,
        viewModelInjector: ViewModelInjector,
        defaultAnimation: NavigationAnimation,
    ): CodeBlock = CodeBlock.builder()
        .beginControlFlow(
            "%M(modifier = modifier, navController = navController, startDestination = startDestination)",
            navHostMember,
        )
        .apply {
            functionList.forEach { function ->
                addScreenComposable(function, hostName, viewModelInjector, defaultAnimation)
            }
        }
        .endControlFlow()
        .build()

    private fun CodeBlock.Builder.addScreenComposable(
        function: KSFunctionDeclaration,
        hostName: String,
        viewModelInjector: ViewModelInjector,
        defaultAnimation: NavigationAnimation,
    ): CodeBlock.Builder {
        val screensEnum = ClassName(packageName, "${hostName}NavigationScreens")
        val screenEntryName = function.toString().replaceScreenWord(screenSuffix)
        val screenFunction = MemberName(function.packageName.asString(), function.simpleName.asString())
        val params = function.parameters.filter { !it.isNavHostController() && !it.isViewModel() }
        val animation = function.getAnimationType(defaultAnimation).type.buildAnimationContent()
        val deepLinks = function.stringListAnnotationParameterByName(KoGenScreen::class, "deepLinks")
        warnAboutUnknownDeepLinkPlaceholders(deepLinks, params, screenEntryName)

        add("%M(\n", composableMember)
        indent()
        add("route = %T.%L.route,\n", screensEnum, screenEntryName)
        if (params.isNotEmpty()) {
            add("arguments = listOf(\n")
            indent()
            params.forEach { param -> add("%L\n", ArgumentTypes.getNavArgsString(param)) }
            unindent()
            add("),\n")
        }
        if (deepLinks.isNotEmpty()) {
            add("deepLinks = listOf(\n")
            indent()
            deepLinks.forEach { pattern -> add("%M { uriPattern = %S },\n", navDeepLinkMember, pattern) }
            unindent()
            add("),\n")
        }
        if (animation.isNotEmpty()) add("%L", animation)
        unindent()
        beginControlFlow(")")

        if (function.parameters.isEmpty()) {
            add("%M()\n", screenFunction)
        } else {
            add("%M(\n", screenFunction)
            indent()
            add("%L", generateScreenParameters(function, viewModelInjector))
            unindent()
            add(")\n")
        }

        endControlFlow()
        return this
    }

    /**
     * Warns (doesn't fail the build) about any `{placeholder}` in a screen's `deepLinks` that
     * doesn't match one of its route parameters - `navDeepLink`'s pattern matching is entirely
     * runtime, so a typo'd or stale placeholder wouldn't otherwise surface until the deep link
     * silently fails to fill that argument.
     */
    private fun warnAboutUnknownDeepLinkPlaceholders(
        deepLinks: List<String>,
        params: List<KSValueParameter>,
        screenEntryName: String,
    ) {
        if (deepLinks.isEmpty()) return
        val paramNames = params.mapNotNull { it.name?.asString() }.toSet()
        deepLinks.forEach { pattern ->
            deepLinkPlaceholder.findAll(pattern).map { it.groupValues[1] }.forEach { placeholder ->
                if (placeholder !in paramNames) {
                    logger.warn(
                        "@KoGenScreen deepLinks: '{$placeholder}' in \"$pattern\" for screen " +
                            "'$screenEntryName' does not match any of its parameters " +
                            "(${paramNames.ifEmpty { setOf("<none>") }})",
                    )
                }
            }
        }
    }

    private fun generateScreenParameters(
        function: KSFunctionDeclaration,
        viewModelInjector: ViewModelInjector,
    ): CodeBlock = CodeBlock.builder().apply {
        function.parameters.forEach { parameter ->
            when {
                parameter.isNavHostController() ->
                    addStatement("%L = navController,", parameter.name?.asString())

                parameter.isViewModel() -> {
                    val injectorFunction = viewModelInjector.injectorFunction
                    if (injectorFunction != null) {
                        addStatement(
                            "%L = %M<%T>(),",
                            parameter.name?.asString(),
                            injectorFunction,
                            parameter.type.resolve().toTypeName(),
                        )
                    }
                }

                else ->
                    addStatement(
                        "%L = %L,",
                        parameter.name?.asString(),
                        ArgumentTypes.getArgumentString(parameter),
                    )
            }
        }
    }.build()
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
