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
import kz.evko.navigation.annotation.KoGenTab
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
 * Finds every `@KoGenScreen`-annotated function (grouped by `navHostName`, into a plain graph) and
 * every `@KoGenTab`-annotated one (grouped by `graph`, into a tab - see its own doc for why a
 * screen carries exactly one of the two, never both), and hands each group to [fileGenerator] to
 * turn into a screens enum + graph - plus, project-wide, the routes/extensions files.
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
 * - `tabsHostName` - the combined `NavHost` function/file's name for this round's own tab graphs
 *   (see `@KoGenTab`), default `"AppTabsHost"` - deliberately not `"AppNavHost"`, the
 *   default `navHostName`: an untagged group named that already owns that exact file (its own
 *   self-contained `NavHost`), which this would otherwise collide with. Only meaningful in
 *   [BuildMode.Single], or in [BuildMode.Module] with `shareTabGraph = false`.
 * - `shareTabGraph` - whether a [BuildMode.Module] module defers wrapping its own tab graphs to a
 *   [BuildMode.Aggregator] (`true`, the default - lets a tab span more than one module) or builds
 *   them itself, locally, via `tabsHostName` (`false` - e.g. this module isn't meant to depend on
 *   ever being combined by one at all). Ignored outside [BuildMode.Module].
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
        val screenFunctionSet = screenFunctions.toSet()

        // @KoGenTab is meant to be used *instead of* @KoGenScreen, never alongside it - stacking
        // both would otherwise register the same screen's composable(...) entry twice (once via
        // each one's own grouping/generation pass below). Not a build failure: @KoGenScreen wins
        // deterministically, @KoGenTab is ignored, and it's reported as a warning.
        val tabFunctionsRaw = resolver.findAnnotations(KoGenTab::class).filterIsInstance<KSFunctionDeclaration>().toList()
        val tabFunctions = tabFunctionsRaw.filterNot { it in screenFunctionSet }
        tabFunctionsRaw.filter { it in screenFunctionSet }.forEach {
            logger.warn(
                "${it.simpleName.asString()} carries both @KoGenScreen and @KoGenTab - only one is " +
                    "supported at a time; using @KoGenScreen, ignoring @KoGenTab.",
            )
        }

        val allFunctions = screenFunctions + tabFunctions

        // Everything in this block writes a file, and needs to run exactly once - see hasRunOnce's
        // own comment. The *return value* below stays outside of it deliberately: KSP's retry
        // mechanism (returning still-invalid symbols for it to hand back on the next round) needs
        // to keep working on every round, not just the first, even though for a normal (nothing
        // stuck as invalid) compilation later rounds simply see zero annotated symbols and this
        // ends up returning emptyList() anyway - same result, but for the right reason.
        if (!hasRunOnce) {
            hasRunOnce = true
            fileGenerator.createPackageName(packageName, allFunctions.asSequence())
            // Module mode skips this on purpose - see BuildMode.Module's own doc comment for why.
            if (buildMode != BuildMode.Module) fileGenerator.createExtensions()

            // This round's own graphs, whether or not this module has any @KoGenScreen/@KoGenTab
            // at all - a plain :app aggregator with none of its own (just combining other
            // modules') is the common case, not something to skip everything else for. Grouped
            // separately: a @KoGenScreen group is always plain, a @KoGenTab group always a tab -
            // see FileWriter.createScreensList's own `isTab` doc for why that's no longer something
            // to resolve/validate at all, unlike when a screen could carry both annotations.
            val plainGraphs: List<GraphManifestEntry> = screenFunctions.groupBy {
                it.stringAnnotationParameterByName(KoGenScreen::class, navHostName)
            }.mapNotNull { (name, functions) ->
                fileGenerator.createScreensList(
                    screensFunctions = functions,
                    name = name,
                    viewModelInjector = viewModelInjector,
                    defaultAnimation = defaultAnimation,
                    screenSuffix = screenSuffix,
                    buildMode = buildMode,
                    annotationClass = KoGenScreen::class,
                    isTab = false,
                )
            }
            val tabGraphs: List<GraphManifestEntry> = tabFunctions.groupBy {
                it.stringAnnotationParameterByName(KoGenTab::class, "graph")
            }.mapNotNull { (name, functions) ->
                fileGenerator.createScreensList(
                    screensFunctions = functions,
                    name = name,
                    viewModelInjector = viewModelInjector,
                    defaultAnimation = defaultAnimation,
                    screenSuffix = screenSuffix,
                    buildMode = buildMode,
                    annotationClass = KoGenTab::class,
                    isTab = true,
                )
            }
            val graphs = plainGraphs + tabGraphs
            if (screenFunctions.isNotEmpty()) fileGenerator.createRoutes(screenFunctions, screenSuffix)
            if (tabFunctions.isNotEmpty()) fileGenerator.createTabRoutes(tabFunctions, screenSuffix)

            // A tab graph resolved locally (never deferred to an aggregator - see BuildMode.Module's
            // own branch below) is either this round's *only* way to end up with a NavHost for it
            // (BuildMode.Single - createGraph() already skipped generating a standalone one for a
            // tab-tagged group) or one this round additionally builds on top of contributing to the
            // manifest as normal (BuildMode.Module, shareTabGraph = false) - either way it's kept
            // out of the manifest, since nothing reads it back from there once it's already handled.
            val shareTabGraph = args["shareTabGraph"]?.toBooleanStrictOrNull() ?: true
            val (localTabGraphs, manifestGraphs) = graphs.partition {
                it.tabGraph != null && (buildMode == BuildMode.Single || (buildMode == BuildMode.Module && !shareTabGraph))
            }

            when (buildMode) {
                BuildMode.Single -> {
                    fileGenerator.createLocalTabbedNavHost(args["tabsHostName"] ?: "AppTabsHost", localTabGraphs)
                }
                BuildMode.Module -> {
                    val moduleName = args["moduleName"]
                    if (moduleName.isNullOrBlank()) {
                        logger.error("buildMode = \"module\" requires the \"moduleName\" KSP option to be set.")
                    } else {
                        fileGenerator.createManifest(moduleName, manifestGraphs, allFunctions)
                    }
                    fileGenerator.createLocalTabbedNavHost(args["tabsHostName"] ?: "AppTabsHost", localTabGraphs)
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

        return allFunctions.filterNot { it.validate() }
    }

    /** Every function annotated with [kClass], regardless of which `navHostName` it belongs to. */
    private fun Resolver.findAnnotations(
        kClass: KClass<*>,
    ) = getSymbolsWithAnnotation(
        kClass.qualifiedName.toString()
    )
}
