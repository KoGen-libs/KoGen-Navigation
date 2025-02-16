package kz.evko.navigation

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import kz.evko.navigation.annotation.NavigationAnimation
import kz.evko.navigation.annotation.ViewModelInjector
import kz.evko.navigation.contentGenerators.NavHostContentGenerator
import kz.evko.navigation.contentGenerators.RoutesListGenerator
import kz.evko.navigation.contentGenerators.ScreenListGenerator
import java.io.OutputStream
import kotlin.reflect.KClass

internal class FileWriter(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) {
    private var packageName = ""

    fun createPackageName(paramsPackageName: String?, functions: Sequence<KSFunctionDeclaration>) {
        packageName = paramsPackageName?.plus(".navigation").takeIf {
            !it.isNullOrEmpty()
        } ?: run {
            val packageParts = functions.firstOrNull()?.packageName?.asString()?.split(".")
            packageParts?.subList(0, 3)?.joinToString(".")?.plus(".navigation") ?: "kz.evko.navigation"
        }
    }

    fun createScreensList(
        screensFunctions: List<KSFunctionDeclaration>,
        name: String,
        viewModelInjector: ViewModelInjector,
        defaultAnimation: NavigationAnimation,
    ) {
        if (!screensFunctions.iterator().hasNext()) return

        val fileName = "${name}NavigationScreens"
        val screenListContentGenerator = ScreenListGenerator(packageName)
        val content = screenListContentGenerator.generateScreenList(
            screensFunctions.toList(),
            fileName,
            logger,
        )

        val file: OutputStream =
            createFile(screensFunctions.toFileList(), fileName)
        file += content
        file.close()

        createNavHost(
            screensFunctions = screensFunctions,
            name = name,
            viewModelInjector = viewModelInjector,
            defaultAnimation = defaultAnimation,
        )
    }

    private fun createNavHost(
        screensFunctions: List<KSFunctionDeclaration>, name: String,
        viewModelInjector: ViewModelInjector,
        defaultAnimation: NavigationAnimation,
    ) {
        val navHostContentGenerator = NavHostContentGenerator(packageName)
        val navHostContent = navHostContentGenerator.generateNavHost(
            functionList = screensFunctions.toList(),
            hostName = name,
            viewModelInjector = viewModelInjector,
            defaultAnimation = defaultAnimation,
        )

        val navHostFile: OutputStream =
            createFile(screensFunctions.toFileList(), name)
        navHostFile += navHostContent
        navHostFile.close()
    }

    fun createRoutes(screensFunctions: List<KSFunctionDeclaration>) {
        val routesContentGenerator = RoutesListGenerator(packageName)

        val routesContent = routesContentGenerator.generateRoutes(
            screensFunctions.toList(),
        )

        val routesFile: OutputStream =
            createFile(screensFunctions.toFileList(), "NavigationRoutes")
        routesFile += routesContent
        routesFile.close()
    }

    fun createExtensions() {
        try {
            val routesContentGenerator = RoutesListGenerator(packageName)
            val extensionsFile: OutputStream =
                createFile(emptyList(), "NavigationExtensions")
            extensionsFile += routesContentGenerator.generateExtensions()
            extensionsFile.close()
        } catch (e: Exception) {
            logger.info(e.message.toString())
        }
    }

    private fun createFile(
        files: List<KSFile>,
        fileName: String,
    ) = codeGenerator.createNewFile(
        Dependencies(
            false,
            *files.toList().toTypedArray(),
        ),
        packageName,
        fileName
    )
}

internal operator fun OutputStream.plusAssign(text: String) {
    write(text.toByteArray())
}

internal fun List<KSDeclaration>.toFileList(): List<KSFile> =
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