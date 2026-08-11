package kz.evko.navigation

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.validate
import kz.evko.navigation.annotation.KoGenScreen
import kz.evko.navigation.helpers.BuildMode
import kz.evko.navigation.helpers.NavigationAnimation
import kz.evko.navigation.helpers.ViewModelInjector
import kz.evko.navigation.manifest.GraphManifestEntry
import kotlin.reflect.KClass

/** KSP entry point (registered via `META-INF/services`) - builds one [ScreenGeneratorProcessor] per compilation. */
class ScreenGeneratorProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val fileWriter = FileWriter(environment.codeGenerator, environment.logger)
        return ScreenGeneratorProcessor(fileWriter, environment.options, environment.logger)
    }
}

/**
 * Finds every `@KoGenScreen`-annotated function, groups them by `navHostName`, and hands each
 * group to [fileGenerator] to turn into a screens enum + `NavHost` - plus, project-wide, the
 * routes/extensions files.
 *
 * Reads these KSP options (`ksp { arg(...) }` in the consuming module's build script), all
 * optional except `moduleName` in [BuildMode.Module]:
 * - `viewModelInjector` - a [ViewModelInjector.diName] (`"koin"`/`"hilt"`), default [ViewModelInjector.None].
 * - `defaultAnimation` - a [NavigationAnimation.typeName], default [NavigationAnimation.None].
 * - `packageName` - see [FileWriter.createPackageName].
 * - `screenSuffix` - stripped from a screen function's name to derive its route/enum-entry/action name.
 * - `buildMode` - a [BuildMode.argName], default [BuildMode.Single].
 * - `moduleName` - this module's own name, used as its manifest's file name and as the `module`
 *   field an aggregator reports in its own error messages. Required in [BuildMode.Module] (there's
 *   no sane default to fall back to); ignored otherwise.
 * - `aggregateManifestsDir` - path to a directory of every [BuildMode.Module]'s manifest, assembled
 *   there by Gradle (see [FileWriter.createAggregatedNavHost]). Required in [BuildMode.Aggregator];
 *   ignored otherwise.
 * - `aggregateHostName` - the combined `NavHost` function/file's name, default `"AppNavHost"`.
 *   Only meaningful in [BuildMode.Aggregator].
 */
internal class ScreenGeneratorProcessor(
    private val fileGenerator: FileWriter,
    private val args: Map<String, String>,
    private val logger: KSPLogger,
) : SymbolProcessor {
    private val navHostName = "navHostName"

    // KSP invokes process() once per round (there are several per compilation, even when nothing
    // changed) on this same processor instance. createPackageName()/createExtensions()/the
    // per-mode finalization step only need to run once - the first round always sees every
    // currently-known @KoGenScreen function, so there's nothing to gain by repeating any of this
    // on later rounds. Without this guard, later rounds see zero annotated symbols (they were
    // already consumed), the package-name inference has nothing to infer from and silently falls
    // back to a hardcoded default, and createExtensions()/createAggregatedNavHost() would try to
    // write their files a second time and crash with FileAlreadyExistsException.
    private var hasRunOnce = false

    /** @return Every annotated function that isn't valid yet, for KSP to retry next round. */
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val viewModelInjector = ViewModelInjector.entries.firstOrNull {
            it.diName == args["viewModelInjector"]
        } ?: ViewModelInjector.None

        val defaultAnimation = NavigationAnimation.entries.firstOrNull {
            it.typeName == args["defaultAnimation"]
        } ?: NavigationAnimation.None

        val buildMode = BuildMode.entries.firstOrNull {
            it.argName == args["buildMode"]
        } ?: BuildMode.Single

        val packageName = args["packageName"]
        val screenSuffix = args["screenSuffix"]

        val screenFunctions: List<KSFunctionDeclaration> =
            resolver.findAnnotations(KoGenScreen::class).filterIsInstance<KSFunctionDeclaration>().toList()

        // Everything in this block writes a file, and needs to run exactly once - see hasRunOnce's
        // own comment. The *return value* below stays outside of it deliberately: KSP's retry
        // mechanism (returning still-invalid symbols for it to hand back on the next round) needs
        // to keep working on every round, not just the first, even though for a normal (nothing
        // stuck as invalid) compilation later rounds simply see zero annotated symbols and this
        // ends up returning emptyList() anyway - same result, but for the right reason.
        if (!hasRunOnce) {
            hasRunOnce = true
            fileGenerator.createPackageName(packageName, screenFunctions.asSequence())
            // Module mode skips this on purpose - see BuildMode.Module's own doc comment for why.
            if (buildMode != BuildMode.Module) fileGenerator.createExtensions()

            // This round's own graphs, whether or not this module has any @KoGenScreen at all - a
            // plain :app aggregator with none of its own (just combining other modules') is the
            // common case, not something to skip everything else for.
            val graphs: List<GraphManifestEntry> = screenFunctions.groupBy {
                it.stringAnnotationParameterByName(KoGenScreen::class, navHostName)
            }.mapNotNull {
                fileGenerator.createScreensList(
                    screensFunctions = it.value,
                    name = it.key,
                    viewModelInjector = viewModelInjector,
                    defaultAnimation = defaultAnimation,
                    screenSuffix = screenSuffix,
                    buildMode = buildMode,
                )
            }
            if (screenFunctions.isNotEmpty()) fileGenerator.createRoutes(screenFunctions, screenSuffix)

            when (buildMode) {
                BuildMode.Single -> Unit
                BuildMode.Module -> {
                    val moduleName = args["moduleName"]
                    if (moduleName.isNullOrBlank()) {
                        logger.error("buildMode = \"module\" requires the \"moduleName\" KSP option to be set.")
                    } else {
                        fileGenerator.createManifest(moduleName, graphs, screenFunctions)
                    }
                }
                BuildMode.Aggregator -> {
                    val manifestsDir = args["aggregateManifestsDir"]
                    if (manifestsDir.isNullOrBlank()) {
                        logger.error("buildMode = \"aggregator\" requires the \"aggregateManifestsDir\" KSP option to be set.")
                    } else {
                        fileGenerator.createAggregatedNavHost(manifestsDir, args["aggregateHostName"] ?: "AppNavHost", graphs)
                    }
                }
            }
        }

        return screenFunctions.filterNot { it.validate() }
    }

    /** Every function annotated with [kClass], regardless of which `navHostName` it belongs to. */
    private fun Resolver.findAnnotations(
        kClass: KClass<*>,
    ) = getSymbolsWithAnnotation(
        kClass.qualifiedName.toString()
    )
}
