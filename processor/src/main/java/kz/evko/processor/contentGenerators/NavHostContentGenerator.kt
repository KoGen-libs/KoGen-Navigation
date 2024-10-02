package kz.evko.processor.contentGenerators

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import kz.evko.annotation.GenerateScreens
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
                        appendLine(parameter.getNavArg())
                    }
                    appendLine("\t\t\t)")

                    appendLine("\t\t) {")
                }

                if (it.parameters.isEmpty()) {
                    appendLine("\t\t\t${it.simpleName.asString()}()")
                } else {
                    appendLine("\t\t\t${it.simpleName.asString()}(")

                    append(generateScreenParameters(it.parameters))

                    appendLine("\t\t\t)")
                }
                appendLine("\t\t}")
            }

            appendLine("\t}")
            appendLine("}")
        }

    private fun generateImports(functionList: List<KSFunctionDeclaration>) = buildString {
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
        append("\n")
    }

    private fun generateNavHostFunction(
        functionList: List<KSFunctionDeclaration>,
        hostName: String
    ) = buildString {
        val startDestination = functionList.firstOrNull {
            it.annotationParameterByName(
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

    private fun generateScreenParameters(parameters: List<KSValueParameter>) = buildString {
        parameters.forEach { parameter ->
            when {
                parameter.isNavHostController() ->
                    appendLine("\t\t\t\t${parameter.name?.asString()} = navController,")

                parameter.isViewModel() -> appendLine("\t\t\t\t${parameter.name?.asString()} = koinViewModel<${parameter.type}>(),")
                else ->
                    appendLine("\t\t\t\t${parameter.name?.asString()} = ${parameter.getArgumentString()},")
            }
        }
    }
}

fun KSValueParameter.getArgumentString(): String {
    val name = name?.asString()
    return when (type.resolve().toString()) {
        "Boolean" -> "it.arguments?.getBoolean(\"$name\") ?: false"
        "String" -> "it.arguments?.getString(\"$name\").orEmpty()"
        "Int" -> "it.arguments?.getInt(\"$name\") ?: 0"
        "Long" -> "it.arguments?.getLong(\"$name\") ?: 0"
        "Float" -> "it.arguments?.getFloat(\"$name\") ?: 0"
        "BooleanArray" -> "it.arguments?.getBooleanArray(\"$name\") ?: booleanArrayOf()"
        "Array<Boolean>" -> "it.arguments?.getBooleanArray(\"$name\")?.toTypedArray() ?: arrayOf()"
        "List<Boolean>" -> "it.arguments?.getBooleanArray(\"$name\")?.toList() ?: listOf()"
        "Array<String>" -> "it.arguments?.getStringArray(\"$name\") ?: arrayOf()"
        "List<String>" -> "it.arguments?.getStringArray(\"$name\")?.toList() ?: listOf()"
        "IntArray" -> "it.arguments?.getIntArray(\"$name\")"
        "Array<Int>" -> "it.arguments?.getIntArray(\"$name\")?.toTypedArray() ?: arrayOf()"
        "List<Int>" -> "it.arguments?.getIntArray(\"$name\")?.toList() ?: listOf()"
        "LongArray" -> "it.arguments?.getLongArray(\"$name\")"
        "Array<Long>" -> "it.arguments?.getLongArray(\"$name\")?.toTypedArray() ?: arrayOf()"
        "List<Long>" -> "it.arguments?.getLongArray(\"$name\")?.toList() ?: listOf()"
        "FloatArray" -> "it.arguments?.getFloatArray(\"$name\")"
        "Array<Float>" -> "it.arguments?.getFloatArray(\"$name\")?.toTypedArray() ?: arrayOf()"
        "List<Float>" -> "it.arguments?.getFloatArray(\"$name\")?.toList() ?: listOf()"
        else -> {
            "Gson().fromJson(it.arguments?.getString(\"$name\").orEmpty(), ${type}::class.java)"
        }
    }
}

fun KSValueParameter.getNavArg(): String {
    val argDefaultValue: String
    val argType: String

    when (type.resolve().toString()) {
        "Boolean" -> {
            argDefaultValue = "false"
            argType = "NavType.BoolType"
        }
        "String" -> {
            argDefaultValue = "\"\""
            argType = "NavType.StringType"
        }
        "Int" -> {
            argDefaultValue = "0"
            argType = "NavType.IntType"
        }
        "Long" -> {
            argDefaultValue = "0"
            argType = "NavType.LongType"
        }
        "Float" -> {
            argDefaultValue = "0"
            argType = "NavType.FloatType"
        }
        "BooleanArray", "Array<Boolean>", "List<Boolean>" -> {
            argDefaultValue = "booleanArrayOf()"
            argType = "NavType.BoolArrayType"
        }
        "Array<String>", "List<String>" -> {
            argDefaultValue = "arrayOf()"
            argType = "NavType.StringArrayType"
        }
        "IntArray", "Array<Int>", "List<Int>" -> {
            argDefaultValue = "intArrayOf()"
            argType = "NavType.IntArrayType"
        }
        "LongArray", "Array<Long>", "List<Long>" -> {
            argDefaultValue = "longArrayOf()"
            argType = "NavType.LongArrayType"
        }
        "FloatArray", "Array<Float>", "List<Float>" -> {
            argDefaultValue = "floatArrayOf()"
            argType = "NavType.FloatArrayType"
        }
        else -> {
            argDefaultValue = "\"\""
            argType = "NavType.StringType"
        }
    }

    return buildString {
        appendLine("\t\t\t\tnavArgument(\"${name?.asString()}\") {")
        appendLine("\t\t\t\t\tdefaultValue = $argDefaultValue")
        appendLine("\t\t\t\t\ttype = $argType")
        append("\t\t\t\t},")
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