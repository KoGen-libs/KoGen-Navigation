package kz.evko.navigation

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.validate
import kz.evko.navigation.annotation.KoGenScreen
import kz.evko.navigation.helpers.NavigationAnimation
import kz.evko.navigation.helpers.ViewModelInjector
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

    // KSP invokes process() once per round (there are several per compilation, even when nothing
    // changed) on this same processor instance. createPackageName()/createExtensions() only need
    // to run once - the first round always sees every currently-known @KoGenScreen function, so
    // there's nothing to gain by repeating it on later rounds. Without this guard, later rounds
    // see zero annotated symbols (they were already consumed), the package-name inference has
    // nothing to infer from and silently falls back to a hardcoded default, and
    // createExtensions() regenerates NavigationExtensions.kt a second time under that wrong
    // package - a stray duplicate file alongside the correct one.
    private var hasGeneratedExtensions = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val viewModelInjector = ViewModelInjector.entries.firstOrNull {
            it.diName == args["viewModelInjector"]
        } ?: ViewModelInjector.None

        val defaultAnimation = NavigationAnimation.entries.firstOrNull {
            it.typeName == args["defaultAnimation"]
        } ?: NavigationAnimation.None

        val packageName = args["packageName"]
        val screenSuffix = args["screenSuffix"]

        val screenFunctions: Sequence<KSFunctionDeclaration> =
            resolver.findAnnotations(KoGenScreen::class).filterIsInstance<KSFunctionDeclaration>()

        if (!hasGeneratedExtensions) {
            hasGeneratedExtensions = true
            fileGenerator.createPackageName(packageName, screenFunctions)
            fileGenerator.createExtensions()
        }

        if (!screenFunctions.iterator().hasNext()) return emptyList()

        screenFunctions.groupBy {
            it.stringAnnotationParameterByName(
                KoGenScreen::class,
                navHostName
            )
        }.forEach {
            fileGenerator.createScreensList(
                screensFunctions = it.value,
                name = it.key,
                viewModelInjector = viewModelInjector,
                defaultAnimation = defaultAnimation,
                screenSuffix = screenSuffix,
            )
        }
        fileGenerator.createRoutes(screenFunctions.toList(), screenSuffix)

        return (screenFunctions).filterNot { it.validate() }.toList()
    }

    private fun Resolver.findAnnotations(
        kClass: KClass<*>,
    ) = getSymbolsWithAnnotation(
        kClass.qualifiedName.toString()
    )
}
