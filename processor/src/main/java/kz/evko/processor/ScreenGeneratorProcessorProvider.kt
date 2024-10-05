package kz.evko.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.validate
import kz.evko.processor.annotation.GenerateScreens
import kotlin.reflect.KClass

class ScreenGeneratorProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val fileWriter = FileWriter(environment.codeGenerator, environment.logger)
        return ScreenGeneratorProcessor(fileWriter)
    }
}

internal class ScreenGeneratorProcessor(
    private val fileGenerator: FileWriter,
) : SymbolProcessor {
    private val navHostName = "navHostName"

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val screenFunctions: Sequence<KSFunctionDeclaration> =
            resolver.findAnnotations(GenerateScreens::class)

        if (!screenFunctions.iterator().hasNext()) return emptyList()

        screenFunctions.groupBy {
            it.annotationParameterByName<String>(
                GenerateScreens::class,
                navHostName
            )
        }.forEach {
            fileGenerator.createScreensListFile(it.value, it.key)
            fileGenerator.createNavHostFile(it.value, it.key)
        }

        return (screenFunctions).filterNot { it.validate() }.toList()
    }

    private fun Resolver.findAnnotations(
        kClass: KClass<*>,
    ) = getSymbolsWithAnnotation(
        kClass.qualifiedName.toString()
    )
        .filterIsInstance<KSFunctionDeclaration>()
}
