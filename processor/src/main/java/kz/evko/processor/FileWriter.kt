package kz.evko.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import kz.evko.processor.contentGenerators.NavHostContentGenerator
import kz.evko.processor.contentGenerators.ScreenListContentGenerator
import java.io.OutputStream
import kotlin.reflect.KClass

internal class FileWriter(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) {
    fun createScreensListFile(screensFunctions: List<KSFunctionDeclaration>, name: String) {
        if (!screensFunctions.iterator().hasNext()) return

        val fileName = "${name}NavigationScreens"
        val screenListContentGenerator = ScreenListContentGenerator()
        val content = screenListContentGenerator.generateContent(
            screensFunctions.toList(),
            fileName,
        )

        val file: OutputStream =
            createSCreensListFile(screensFunctions.toFileList(), fileName)
        file += content
        file.close()
    }

    fun createNavHostFile(screenFunctions: List<KSFunctionDeclaration>, hostName: String) {
        if (!screenFunctions.iterator().hasNext()) return

        val navHostContentGenerator = NavHostContentGenerator()
        val content = navHostContentGenerator.generateContent(
            screenFunctions.toList(),
            hostName,
        )

        val file: OutputStream =
            createSCreensListFile(screenFunctions.toFileList(), hostName)
        file += content
        file.close()
    }

    private fun createSCreensListFile(
        files: List<KSFile>,
        fileName: String,
    ) = codeGenerator.createNewFile(
        Dependencies(
            false,
            *files.toList().toTypedArray(),
        ),
        kspPackage(),
        fileName
    )
}

internal fun kspPackage() = "kz.evko.navigationplugin"

internal operator fun OutputStream.plusAssign(text: String) {
    write(text.toByteArray())
}

internal fun List<KSFunctionDeclaration>.toFileList(): List<KSFile> =
    mapNotNull { it.containingFile }

internal fun <T> KSFunctionDeclaration.annotationParameterByName(
    annotationClass: KClass<*>,
    parameterName: String
): T {
    val annotation =
        annotations.first { it.shortName.asString() == annotationClass.simpleName.toString() }
    val name = annotation.arguments.first { it.name?.asString() == parameterName }
    return name.value as T
}

internal fun KSFunctionDeclaration.annotationParameterByName(
    annotationClass: KClass<*>,
    parameterName: String
): String {
    val annotation =
        annotations.first { it.shortName.asString() == annotationClass.simpleName.toString() }
    val name = annotation.arguments.first { it.name?.asString() == parameterName }
    return name.value?.toString()?.split(".")?.lastOrNull() ?: ""
}

internal fun String.replaceScreenWord(): String {
    return replace("screen", "", ignoreCase = true)
}