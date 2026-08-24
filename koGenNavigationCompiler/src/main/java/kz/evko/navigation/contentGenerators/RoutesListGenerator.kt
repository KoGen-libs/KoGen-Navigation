package kz.evko.navigation.contentGenerators

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.ksp.toTypeName
import kz.evko.navigation.replaceScreenWord

/**
 * Builds `NavigationRoutes.kt` (one `ActionTo<Screen>` per screen, via [generateRoutes]) and
 * `NavigationExtensions.kt` (the fixed `navigateSafety`/`popBackSafety`/`getResultData` helpers,
 * via [generateExtensions]) - the two files every KSP round emits regardless of `navHostName`
 * grouping.
 */
class RoutesListGenerator(
    private val packageName: String,
    private val screenSuffix: String? = null,
) {
    private val navigationAction = ClassName("kz.evko.navigation.routes", "NavigationAction")

    /**
     * One `ActionTo<Screen>` per screen in [functionList]: a parameterless `object` for a screen
     * with no route arguments, otherwise a `class` whose constructor mirrors the screen function's
     * own arguments and whose `route` is built from them (via [ArgumentTypes], or a Gson-encoded
     * query parameter for anything [ArgumentTypes] doesn't recognize).
     */
    fun generateRoutes(functionList: List<KSFunctionDeclaration>): FileSpec {
        val paramTypes: MutableMap<KSValueParameter, ArgumentTypes> = mutableMapOf()
        val fileBuilder = FileSpec.builder(packageName, "NavigationRoutes")

        functionList.forEach { function ->
            val params = function.parameters.filter { param ->
                !param.isNavHostController() && !param.isViewModel()
            }
            val screenName = function.toString().replaceScreenWord(screenSuffix)

            if (params.isEmpty()) {
                fileBuilder.addType(
                    TypeSpec.objectBuilder("ActionTo$screenName")
                        .addModifiers(KModifier.DATA)
                        .superclass(navigationAction)
                        .addSuperclassConstructorParameter("route = %S", screenName.lowercase())
                        .build(),
                )
            } else {
                val constructor = FunSpec.constructorBuilder()
                params.forEach { param ->
                    val resolved = param.type.resolve()
                    val isNullable = resolved.isMarkedNullable
                    val hasDefault = param.hasDefault

                    ArgumentTypes.findType(param)?.let { paramTypes[param] = it }

                    val parameterSpec = ParameterSpec.builder(param.name!!.asString(), resolved.toTypeName())
                    if (isNullable && hasDefault) parameterSpec.defaultValue("null")
                    constructor.addParameter(parameterSpec.build())
                }

                // The route is a Kotlin string *template* referencing the constructor params by
                // name ("details?id=$id&..."), not a literal - built as source text (emitted
                // verbatim via %L) rather than through %S, which would escape/quote it as data.
                //
                // Every value is URL-encoded, native type or Gson JSON fallback alike - either one
                // can contain a route-special character in the value itself (`&` above all: left
                // unencoded, it silently truncates the rest of the route with no error at all,
                // rather than the crash you might expect) - ArgumentTypes.getArgumentString does
                // the matching URLDecoder.decode on the read-back side.
                val routeTemplate = params.joinToString(
                    separator = "&",
                    prefix = "${screenName.lowercase()}?",
                ) { param ->
                    paramTypes[param]?.let { "$param=\${java.net.URLEncoder.encode($param.toString(), \"UTF-8\")}" }
                        ?: "$param=\${java.net.URLEncoder.encode(com.google.gson.Gson().toJson($param), \"UTF-8\")}"
                }

                fileBuilder.addType(
                    TypeSpec.classBuilder("ActionTo$screenName")
                        .primaryConstructor(constructor.build())
                        .superclass(navigationAction)
                        .addSuperclassConstructorParameter("route = %L", "\"$routeTemplate\"")
                        .build(),
                )
            }
        }

        return fileBuilder.build()
    }

    /**
     * The three helpers every consumer gets regardless of their screens: `navigateSafety` (logs,
     * then navigates - with optional `popUpTo`), `popBackSafety` (logs, optionally stashes a
     * `BackStackData` result, then pops), and `getResultData` (reads
     * that result back out, clearing it by default). `BackStackData` isn't a compile dependency of
     * this module, so it's referenced here by name rather than as a resolvable KDoc link.
     *
     * All three now also live as real, hand-written functions in `koGenNavigation` itself (under
     * `kz.evko.navigation.helpers`) - the runtime dependency the Gradle plugin adds is now always
     * pinned to the matching version, so there's no need to keep re-emitting identical text into
     * every consumer. These generated copies are kept, `@Deprecated`, for one transitional release
     * so upgrading doesn't break existing call sites; drop [generateExtensions] entirely once that
     * transition period is over.
     */
    fun generateExtensions(): FileSpec {
        val navHostController = ClassName("androidx.navigation", "NavHostController")
        val routeScreenType = ClassName("kz.evko.navigation.routes", "RouteScreenType")
        val backStackData = ClassName("kz.evko.navigation.helpers", "BackStackData")
        val navigationResultKey = ClassName("kz.evko.navigation.helpers", "NavigationResultKey")
        val typeVariableT = TypeVariableName("T")

        val navigateSafety = FunSpec.builder("navigateSafety")
            .addAnnotation(
                deprecatedInFavorOfRuntime(
                    "navigateSafety(action, popUpTo, inclusive)",
                    "kz.evko.navigation.helpers.navigateSafety",
                ),
            )
            .addKdoc(
                """
                |Logs the navigation, then navigates to [action]'s route.
                |
                |@param action Screen to navigate to.
                |@param popUpTo Also pop the back stack up to this destination first, if given.
                |@param inclusive Whether [popUpTo] itself is popped too, not just what's above it.
                """.trimMargin(),
            )
            .receiver(navHostController)
            .addParameter("action", navigationAction)
            .addParameter(
                ParameterSpec.builder("popUpTo", routeScreenType.copy(nullable = true))
                    .defaultValue("null")
                    .build(),
            )
            .addParameter(ParameterSpec.builder("inclusive", BOOLEAN).defaultValue("false").build())
            .addCode(
                """
                Log.d("NavigateSafety", action.navigationLog(popUpTo, inclusive))

                navigate(action.route) {
                    popUpTo?.let {
                        popUpTo(it.route) {
                            this.inclusive = inclusive
                        }
                    }
                }

                """.trimIndent(),
            )
            .build()

        val popBackSafety = FunSpec.builder("popBackSafety")
            .addAnnotation(
                deprecatedInFavorOfRuntime(
                    "popBackSafety(backStackData)",
                    "kz.evko.navigation.helpers.popBackSafety",
                ),
            )
            .addKdoc(
                """
                |Logs the pop, optionally stashes [backStackData] for the screen being returned to
                |(read it back there via [getResultData]), then pops the back stack.
                |
                |@param backStackData Result to hand back to the previous screen, if any.
                """.trimMargin(),
            )
            .receiver(navHostController)
            .addParameter(
                ParameterSpec.builder("backStackData", backStackData.parameterizedBy(STAR).copy(nullable = true))
                    .defaultValue("null")
                    .build(),
            )
            .addCode(
                """
                if (previousBackStackEntry != null) {
                    Log.d(
                        "PopBackSafety",
                        kz.evko.navigation.routes.navigationBackLog(
                            fromScreen = this@popBackSafety.currentDestination?.route?.split("?")
                                ?.firstOrNull()?.capitalize(Locale.current),
                            toScreen = this@popBackSafety.previousBackStackEntry?.destination?.route?.split("?")
                                ?.firstOrNull()?.capitalize(Locale.current)
                        )
                    )

                    backStackData?.let {
                        previousBackStackEntry?.savedStateHandle?.set(it.data.key, it.value)
                    }

                    popBackStack()
                }

                """.trimIndent(),
            )
            .build()

        val getResultData = FunSpec.builder("getResultData")
            .addAnnotation(
                deprecatedInFavorOfRuntime(
                    "getResultData(data, clearData)",
                    "kz.evko.navigation.helpers.getResultData",
                ),
            )
            .addKdoc(
                """
                |Reads back a result previously stashed via [popBackSafety], or `null` if none was.
                |
                |@param data Which result slot to read.
                |@param clearData Whether to clear the stashed value after reading it.
                |@return The stashed value, or `null` if nothing was stashed.
                """.trimMargin(),
            )
            .addTypeVariable(typeVariableT)
            .receiver(navHostController)
            .addParameter("data", navigationResultKey.parameterizedBy(typeVariableT))
            .addParameter(ParameterSpec.builder("clearData", BOOLEAN).defaultValue("true").build())
            .returns(typeVariableT.copy(nullable = true))
            .addCode(
                """
                val result = this.currentBackStackEntry?.savedStateHandle?.get(data.key) as T?
                if (clearData) this.currentBackStackEntry?.savedStateHandle?.remove<T>(data.key)
                return result

                """.trimIndent(),
            )
            .build()

        return FileSpec.builder(packageName, "NavigationExtensions")
            .addImport("android.util", "Log")
            .addImport("androidx.compose.ui.text", "capitalize")
            .addImport("androidx.compose.ui.text.intl", "Locale")
            .addImport("kz.evko.navigation.routes", "navigationLog")
            .addFunction(navigateSafety)
            .addFunction(popBackSafety)
            .addFunction(getResultData)
            .build()
    }

    /**
     * `@Deprecated(message = ..., replaceWith = ReplaceWith([expression], [replacementFqName]))` -
     * points a generated helper at its real, hand-written counterpart living in `koGenNavigation`
     * (see [generateExtensions]'s own kdoc for why the generated copy still exists at all).
     */
    private fun deprecatedInFavorOfRuntime(expression: String, replacementFqName: String): AnnotationSpec =
        AnnotationSpec.builder(Deprecated::class)
            .addMember(
                "message = %S",
                "Moved into the library itself - this generated copy will be removed in a future release.",
            )
            .addMember(
                "replaceWith = %T(%S, %S)",
                ClassName("kotlin", "ReplaceWith"),
                expression,
                replacementFqName,
            )
            .build()
}
