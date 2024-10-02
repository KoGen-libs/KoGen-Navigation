package kz.evko.processor.contentGenerators

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import kz.evko.processor.kspPackage
import kz.evko.processor.replaceScreenWord

internal class ScreenListContentGenerator {
    fun generateContent(
        functionList: List<KSFunctionDeclaration>,
        className: String,
    ): String {
        return generateTexts(functionList, className)
    }

    private fun generateTexts(
        functionList: List<KSFunctionDeclaration>,
        className: String,
    ): String {
        return buildString {
            appendLine("package ${kspPackage()}\n")

            appendLine("import kz.evko.processor.routeActions.NavigationRouteScreens\n")

            appendLine("enum class $className: NavigationRouteScreens {")

            appendLine(
                functionList.joinToString(
                    separator = ",\n\t",
                    prefix = "\t",
                    postfix = ",",
                ) {
                    it.toString().replaceScreenWord()
                }
            )

            append("}")
        }
    }
}