package kz.evko.navigation.contentGenerators

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import kz.evko.navigation.replaceScreenWord

internal class ScreenListGenerator(
    private val packageName: String,
) {
    fun generateScreenList(
        functionList: List<KSFunctionDeclaration>,
        className: String,
        logger: KSPLogger,
    ): String {
        logger.warn("> for $className found ${functionList.size} screens")
        return buildString {
            appendLine("package $packageName\n")

            appendLine("enum class $className(override val route: String): kz.evko.navigation.routes.RouteScreenType {")

            appendLine(
                functionList.joinToString(
                    separator = ",\n\t",
                    prefix = "\t",
                    postfix = ",",
                ) {
                    val params = it.parameters.filter { parameter ->
                        !parameter.isViewModel() && !parameter.isNavHostController()
                    }
                    val screenName = it.toString().replaceScreenWord()
                    if (params.isEmpty()) "$screenName(\"${screenName.lowercase()}\")"
                    else {
                        it.toString().replaceScreenWord() + "(\"" +
                                screenName.lowercase() +
                                params.joinToString(
                                    separator = "&",
                                    prefix = "?",
                                ) { param ->
                                    "${param}={$param}"
                                } + "\")"
                    }
                }
            )

            append("}")
        }
    }
}