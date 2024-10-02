package kz.evko.processor.contentGenerators

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
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

            append(
                generateImports(functionList)
            )

            append(generateNavHostFunction(functionList, hostName))

            functionList.forEach {
                appendLine(
                    "\t\tcomposable(${hostName}NavigationScreens.${
                        it.toString().replaceScreenWord()
                    }.name) {"
                )

                if (it.parameters.isEmpty()) {
                    appendLine("\t\t\t${it.simpleName.asString()}()")
                } else {
                    appendLine("\t\t\t${it.simpleName.asString()}(")

                    append(generateParameters(it.parameters))

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
        appendLine("import androidx.navigation.compose.NavHost")
        appendLine("import androidx.navigation.compose.composable")
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

    private fun generateParameters(parameters: List<KSValueParameter>) = buildString {
        parameters.forEach { parameter ->
            when {
                parameter.type.toString() == "NavHostController" ->
                    appendLine("\t\t\t\t${parameter.name?.asString()} = navController,")
                parameter.isViewModel() -> appendLine("				${parameter.name?.asString()} = koinViewModel(),")
                else -> {
                    val name = parameter.name?.asString()
                    val value = when (parameter.type.resolve().toString()) {
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
                        "LongArray" -> "it.arguments?.getLongArray(\"$name\")"
                        "FloatArray" -> "it.arguments?.getFloatArray(\"$name\")"
                        else -> {
                            "Gson().fromJson(it.arguments?.getString(\"$name\").orEmpty(), ${parameter.type}::class.java)"
                        }
                    }
                    appendLine("\t\t\t\t$name = $value,")
                }
            }
        }
    }
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