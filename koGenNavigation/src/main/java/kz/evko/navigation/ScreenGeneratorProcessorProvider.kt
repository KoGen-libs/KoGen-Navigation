package kz.evko.navigation

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.validate
import kz.evko.navigation.annotation.KoGenScreen
import kz.evko.navigation.annotation.NavigationAnimation
import kz.evko.navigation.annotation.ViewModelInjector
import kotlin.reflect.KClass

class ScreenGeneratorProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val fileWriter = FileWriter(environment.codeGenerator, environment.logger)
        return ScreenGeneratorProcessor(fileWriter, environment.options)
    }
}

internal class ScreenGeneratorProcessor(
    private val fileGenerator: FileWriter,
    private val args: Map<String, String>,
) : SymbolProcessor {
    private val navHostName = "navHostName"

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val viewModelInjector = ViewModelInjector.entries.firstOrNull {
            it.diName == args["viewModelInjector"]
        } ?: ViewModelInjector.None

        val defaultAnimation = NavigationAnimation.entries.firstOrNull {
            it.typeName == args["defaultAnimation"]
        } ?: NavigationAnimation.None

        val packageName = args["packageName"]

        val screenFunctions: Sequence<KSFunctionDeclaration> =
            resolver.findAnnotations(KoGenScreen::class).filterIsInstance<KSFunctionDeclaration>()

        fileGenerator.createPackageName(packageName, screenFunctions)
        fileGenerator.createExtensions()

        if (!screenFunctions.iterator().hasNext()) return emptyList()

        screenFunctions.groupBy {
            it.annotationParameterByName<String>(
                KoGenScreen::class,
                navHostName
            )
        }.forEach {
            fileGenerator.createScreensList(
                screensFunctions = it.value,
                name = it.key,
                viewModelInjector = viewModelInjector,
                defaultAnimation = defaultAnimation,
            )
        }
        fileGenerator.createRoutes(screenFunctions.toList())

        return (screenFunctions).filterNot { it.validate() }.toList()
    }

    private fun Resolver.findAnnotations(
        kClass: KClass<*>,
    ) = getSymbolsWithAnnotation(
        kClass.qualifiedName.toString()
    )
}
