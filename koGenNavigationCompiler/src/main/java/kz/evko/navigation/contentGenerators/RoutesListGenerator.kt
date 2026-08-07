package kz.evko.navigation.contentGenerators

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
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

class RoutesListGenerator(
    private val packageName: String,
    private val screenSuffix: String? = null,
) {
    private val navigationAction = ClassName("kz.evko.navigation.routes", "NavigationAction")

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
                val routeTemplate = params.joinToString(
                    separator = "&",
                    prefix = "${screenName.lowercase()}?",
                ) { param ->
                    paramTypes[param]?.let { "$param=\$$param" }
                        ?: "$param=\${com.google.gson.Gson().toJson($param)}"
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

    fun generateExtensions(): FileSpec {
        val navHostController = ClassName("androidx.navigation", "NavHostController")
        val routeScreenType = ClassName("kz.evko.navigation.routes", "RouteScreenType")
        val backStackData = ClassName("kz.evko.navigation.helpers", "BackStackData")
        val navigationResultKey = ClassName("kz.evko.navigation.helpers", "NavigationResultKey")
        val typeVariableT = TypeVariableName("T")

        val navigateSafety = FunSpec.builder("navigateSafety")
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
}
