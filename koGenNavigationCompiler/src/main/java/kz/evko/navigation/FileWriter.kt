package kz.evko.navigation

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.gson.Gson
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.writeTo
import kz.evko.navigation.contentGenerators.AggregatorContentGenerator
import kz.evko.navigation.contentGenerators.NavHostContentGenerator
import kz.evko.navigation.contentGenerators.RoutesListGenerator
import kz.evko.navigation.contentGenerators.ScreenListGenerator
import kz.evko.navigation.contentGenerators.findPreferredStartDestination
import kz.evko.navigation.contentGenerators.toRoutePattern
import kz.evko.navigation.helpers.BuildMode
import kz.evko.navigation.helpers.NavigationAnimation
import kz.evko.navigation.helpers.ViewModelInjector
import kz.evko.navigation.manifest.GraphManifestEntry
import kz.evko.navigation.manifest.ManifestValidator
import kz.evko.navigation.manifest.ModuleManifest
import kz.evko.navigation.manifest.ScreenManifestEntry
import java.io.File
import kotlin.reflect.KClass

/**
 * Owns the package name every generated file is written under, and dispatches to the three
 * [ScreenListGenerator]/[NavHostContentGenerator]/[RoutesListGenerator] content generators,
 * writing whatever [FileSpec] each of them builds via [writeToGenerated].
 */
internal class FileWriter(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) {
    private var packageName = ""

    /**
     * Resolves [packageName] once per KSP run: the `packageName` KSP option if set, otherwise the
     * first three segments of the first annotated screen's own package plus `.navigation`, falling
     * back to `kz.evko.navigation` if there's no screen to infer one from at all.
     */
    fun createPackageName(paramsPackageName: String?, functions: Sequence<KSFunctionDeclaration>) {
        packageName = paramsPackageName?.plus(".navigation").takeIf {
            !it.isNullOrEmpty()
        } ?: run {
            val packageParts = functions.firstOrNull()?.packageName?.asString()?.split(".")
            packageParts?.take(3)?.joinToString(".")?.plus(".navigation")
                ?: "kz.evko.navigation"
        }
    }

    /**
     * Writes `<name>NavigationScreens.kt` for one `navHostName`/[KoGenTab.graph] group, then its
     * graph via [createGraph]. No-ops for an empty group.
     *
     * @param annotationClass `KoGenScreen::class` for a plain group, `KoGenTab::class` for a tab.
     * @param isTab Whether this group is a tab (grouped by `@KoGenTab(graph = name)`) rather than a
     *   plain one (grouped by `@KoGenScreen(navHostName = name)`).
     * @return This group's [GraphManifestEntry] - for [createManifest] to report, in
     *   [BuildMode.Module], or for [createAggregatedNavHost] to call directly alongside every
     *   other module's, in [BuildMode.Aggregator]. `null` for a plain group in [BuildMode.Single]
     *   (which has no use for one) or an empty group.
     */
    fun createScreensList(
        screensFunctions: List<KSFunctionDeclaration>,
        name: String,
        viewModelInjector: ViewModelInjector,
        defaultAnimation: NavigationAnimation,
        screenSuffix: String?,
        buildMode: BuildMode,
        annotationClass: KClass<*>,
        isTab: Boolean,
    ): GraphManifestEntry? {
        if (!screensFunctions.iterator().hasNext()) return null

        val fileName = "${name}NavigationScreens"
        val screenListContentGenerator = ScreenListGenerator(packageName, screenSuffix)
        val fileSpec = screenListContentGenerator.generateScreenList(
            screensFunctions.toList(),
            fileName,
            logger,
        )
        fileSpec.writeToGenerated(screensFunctions)

        return createGraph(
            screensFunctions = screensFunctions,
            name = name,
            viewModelInjector = viewModelInjector,
            defaultAnimation = defaultAnimation,
            screenSuffix = screenSuffix,
            buildMode = buildMode,
            annotationClass = annotationClass,
            isTab = isTab,
        )
    }

    /**
     * Writes this group's graph: `<name>.kt` (a self-contained `NavHost`) for a plain group in
     * [BuildMode.Single], or `<name>Graph.kt` (a `NavGraphBuilder` extension - see
     * [NavHostContentGenerator.generateGraphExtension]) otherwise - [BuildMode.Module]/[BuildMode.Aggregator]
     * always (an aggregator's own local screens, if it has any, need to end up callable from *its
     * own* combined `NavHost` exactly the same way every other module's do, not a second, rival
     * self-contained one under the same default name), and any tab group regardless of build mode
     * - a tab is always nested inside a shared `NavHost` (see [createLocalTabbedNavHost]/
     * `createAggregatedNavHost`), never its own separate one.
     *
     * @return `null` for a plain group in [BuildMode.Single] (which has no use for a manifest
     *   entry) or an empty group - a real entry otherwise, [GraphManifestEntry.tabGraph] set to
     *   [name] when [isTab].
     */
    private fun createGraph(
        screensFunctions: List<KSFunctionDeclaration>,
        name: String,
        viewModelInjector: ViewModelInjector,
        defaultAnimation: NavigationAnimation,
        screenSuffix: String?,
        buildMode: BuildMode,
        annotationClass: KClass<*>,
        isTab: Boolean,
    ): GraphManifestEntry? {
        val navHostContentGenerator = NavHostContentGenerator(packageName, screenSuffix, logger)

        if (buildMode == BuildMode.Single && !isTab) {
            navHostContentGenerator.generateNavHost(
                functionList = screensFunctions.toList(),
                hostName = name,
                viewModelInjector = viewModelInjector,
                defaultAnimation = defaultAnimation,
            ).writeToGenerated(screensFunctions)
            return null
        }

        navHostContentGenerator.generateGraphExtension(
            functionList = screensFunctions.toList(),
            hostName = name,
            viewModelInjector = viewModelInjector,
            defaultAnimation = defaultAnimation,
            annotationClass = annotationClass,
        ).writeToGenerated(screensFunctions)

        val startDestination = screensFunctions.findPreferredStartDestination(annotationClass, logger)
        return GraphManifestEntry(
            graphFunctionName = "${name}Graph",
            screens = screensFunctions.map { function ->
                ScreenManifestEntry(
                    route = function.toRoutePattern(screenSuffix),
                    name = function.toString().replaceScreenWord(screenSuffix),
                    isStartDestination = function == startDestination,
                )
            },
            tabGraph = if (isTab) name else null,
        )
    }

    /**
     * Writes `META-INF/kogen-navigation/<moduleName>.json` - a [ModuleManifest] listing every
     * [BuildMode.Module] `navHostName` group's [GraphManifestEntry] from this KSP run, for an
     * aggregator elsewhere to pick up. As an ordinary KSP resource (not a Kotlin/Java source
     * file), so it ends up in this module's normal build output, not compiled - see
     * `CodeGenerator.createNewFileByPath`.
     *
     * No-ops if [graphs] is empty - nothing for an aggregator to read means nothing worth writing.
     */
    fun createManifest(moduleName: String, graphs: List<GraphManifestEntry>, screensFunctions: List<KSFunctionDeclaration>) {
        if (graphs.isEmpty()) return

        val manifest = ModuleManifest(module = moduleName, packageName = packageName, graphs = graphs)
        val dependencies = Dependencies(true, *screensFunctions.toFileList().toTypedArray())
        codeGenerator.createNewFileByPath(dependencies, "META-INF/kogen-navigation/$moduleName", "json")
            .writer().use { it.write(Gson().toJson(manifest)) }
    }

    /** Writes `NavigationRoutes.kt` - every screen across every `navHostName` group gets an `ActionTo<Screen>`. */
    fun createRoutes(screensFunctions: List<KSFunctionDeclaration>, screenSuffix: String?) {
        val routesContentGenerator = RoutesListGenerator(packageName, screenSuffix)
        val fileSpec = routesContentGenerator.generateRoutes(screensFunctions.toList())
        fileSpec.writeToGenerated(screensFunctions)
    }

    /** Writes `NavigationExtensions.kt` - the fixed `navigateSafety`/`popBackSafety`/`getResultData` helpers. */
    fun createExtensions() {
        // No screen name to strip a suffix from here - generateExtensions() doesn't use it.
        val routesContentGenerator = RoutesListGenerator(packageName, screenSuffix = null)
        routesContentGenerator.generateExtensions().writeToGenerated(emptyList())
    }

    /**
     * Writes `<hostName>.kt` - a `NavHost` combining [tabGraphs] (this KSP round's own tab-tagged
     * `navHostName` groups - never deferred to a `BuildMode.Aggregator`, either because this is
     * [BuildMode.Single] or because the `shareTabGraph` KSP option is `false` in [BuildMode.Module])
     * into one shared graph - exactly what `createAggregatedNavHost` does across modules, just from
     * this round's own in-memory graphs: nothing here ever crossed a module boundary, so there's no
     * manifest file to read back for it. No-ops if [tabGraphs] is empty.
     */
    fun createLocalTabbedNavHost(hostName: String, tabGraphs: List<GraphManifestEntry>) {
        if (tabGraphs.isEmpty()) return

        val manifest = ModuleManifest(module = "(this module)", packageName = packageName, graphs = tabGraphs)
        val validator = ManifestValidator(listOf(manifest), logger)
        AggregatorContentGenerator(packageName).generateAppNavHost(
            manifests = listOf(manifest),
            hostName = hostName,
            startDestinationRoute = validator.resolveStartDestinationRoute(),
            tabStartDestinations = validator.resolveTabStartDestinations(),
        ).writeToGenerated(emptyList())
    }

    /**
     * Reads every `*.json` manifest directly under [manifestsDirPath] (each written by a
     * [BuildMode.Module] module's [createManifest] - collected there by Gradle, not by KSP, see
     * the module's own KDoc for why), combines them with [ownGraphs] (this aggregator's *own*
     * local screens, if it has any - see [createGraph]), validates the whole set together via
     * [ManifestValidator], and writes the combined `<hostName>.kt` - one `NavHost` calling every
     * one of those graph functions, so they all end up sharing one graph/back stack.
     *
     * These manifest files live outside the current KSP compilation entirely (an aggregator can't
     * see the module that wrote one as a live symbol), so - unlike every other generated file -
     * this one has no [KSFunctionDeclaration] to build a [Dependencies] from; whether a changed
     * manifest actually triggers a re-run of this KSP task at all is instead the responsibility of
     * whatever Gradle task assembles [manifestsDirPath] in the first place (registering it as that
     * task's own input), not something expressible here.
     */
    fun createAggregatedNavHost(manifestsDirPath: String, hostName: String, ownGraphs: List<GraphManifestEntry>) {
        val manifestsDir = File(manifestsDirPath)
        if (!manifestsDir.isDirectory) {
            logger.error("buildMode = \"aggregator\": \"$manifestsDirPath\" is not a directory - nothing to aggregate.")
            return
        }

        val gson = Gson()
        // Recursive, not listFiles() - whatever Gradle mechanism assembles manifestsDirPath may
        // well preserve each source's own relative path (e.g. a Sync task copying a whole
        // "resources" tree keeps its "META-INF/kogen-navigation/..." prefix intact), not flatten
        // every *.json straight into this directory's root.
        val manifests = manifestsDir.walkTopDown().filter { it.isFile && it.extension == "json" }.toList()
            .mapNotNull { file ->
                runCatching { gson.fromJson(file.readText(), ModuleManifest::class.java) }
                    .onFailure { logger.error("buildMode = \"aggregator\": couldn't parse manifest \"${file.name}\": ${it.message}") }
                    .getOrNull()
            } + if (ownGraphs.isEmpty()) {
            emptyList()
        } else {
            listOf(ModuleManifest(module = "(this module)", packageName = packageName, graphs = ownGraphs))
        }

        if (manifests.isEmpty()) {
            logger.warn(
                "buildMode = \"aggregator\": no manifests found under \"$manifestsDirPath\" (and no local " +
                    "screens of its own) - $hostName won't have any screens at all.",
            )
        }

        val validator = ManifestValidator(manifests, logger)
        validator.validateNoDuplicateRoutes()

        AggregatorContentGenerator(packageName).generateAppNavHost(
            manifests = manifests,
            hostName = hostName,
            startDestinationRoute = validator.resolveStartDestinationRoute(),
            tabStartDestinations = validator.resolveTabStartDestinations(),
        ).writeToGenerated(emptyList())
    }

    /**
     * Writes this [FileSpec] out via the kotlinpoet-ksp helper - it derives the target
     * package/file name straight from the [FileSpec] itself (matching what we built it with),
     * instead of us re-deriving [Dependencies] and a raw [CodeGenerator.createNewFile] call by
     * hand for every single generated file, as before.
     */
    private fun FileSpec.writeToGenerated(screensFunctions: List<KSFunctionDeclaration>) {
        writeTo(codeGenerator, Dependencies(true, *screensFunctions.toFileList().toTypedArray()))
    }
}

/** The distinct source files these declarations came from - what a KSP [Dependencies] needs to track. */
internal fun List<KSDeclaration>.toFileList(): List<KSFile> =
    mapNotNull { it.containingFile }

/** Untyped annotation-argument lookup underlying every `...AnnotationParameterByName` helper below. */
private fun KSFunctionDeclaration.rawAnnotationParameterValue(
    annotationClass: KClass<*>,
    parameterName: String,
): Any? {
    val annotation =
        annotations.first { it.shortName.asString() == annotationClass.simpleName.toString() }
    return annotation.arguments.first { it.name?.asString() == parameterName }.value
}

/**
 * Reads [parameterName] off an annotation of type [annotationClass], as-is (e.g. `navHostName`
 * on `@KoGenScreen`, which is declared as a plain `String`).
 *
 * Was previously a `fun <T> ...(): T { ... return value as T }` - a non-reified generic cast is
 * erased to `as Any?` at the bytecode level, so it can never actually fail *here*; a caller
 * mismatching `T` against the parameter's real type wouldn't get a `ClassCastException` until
 * some unrelated point downstream where the value is finally used as that (wrong) type. Splitting
 * it into one dedicated, safely-cast function per concrete type actually used fails at the source
 * of the problem instead.
 */
internal fun KSFunctionDeclaration.stringAnnotationParameterByName(
    annotationClass: KClass<*>,
    parameterName: String,
): String = rawAnnotationParameterValue(annotationClass, parameterName) as? String ?: ""

/** Same as [stringAnnotationParameterByName], for a `Boolean`-typed annotation parameter (e.g. `startDestination`). */
internal fun KSFunctionDeclaration.booleanAnnotationParameterByName(
    annotationClass: KClass<*>,
    parameterName: String,
): Boolean = rawAnnotationParameterValue(annotationClass, parameterName) as? Boolean ?: false

/**
 * Same as [stringAnnotationParameterByName], for an `Array<String>`-typed annotation parameter
 * (e.g. `deepLinks`). KSP hands back an array-valued annotation argument as a `List<*>` (not an
 * actual array), so this reads it as one and filters down to the `String` entries.
 */
internal fun KSFunctionDeclaration.stringListAnnotationParameterByName(
    annotationClass: KClass<*>,
    parameterName: String,
): List<String> = (rawAnnotationParameterValue(annotationClass, parameterName) as? List<*>)
    .orEmpty()
    .filterIsInstance<String>()

/**
 * Reads [parameterName] off an annotation of type [annotationClass] and stringifies it, keeping
 * only the part after the last `.` - meant for an enum-typed parameter (e.g. `animation` on
 * `@KoGenScreen`), whose value prints as its fully-qualified name
 * (`kz.evko.navigation.helpers.NavigationAnimation.SlideLeft`) and needs just the entry's own name
 * (`SlideLeft`) to match against `NavigationAnimation.entries`.
 */
internal fun KSFunctionDeclaration.annotationParameterByName(
    annotationClass: KClass<*>,
    parameterName: String
): String = rawAnnotationParameterValue(annotationClass, parameterName)
    ?.toString()?.split(".")?.lastOrNull() ?: ""

/**
 * Strips [suffix] from this screen function's name, used to derive its route/enum-entry/action
 * name (e.g. "HomeScreen" -> "Home"). Only the *last* occurrence is removed, so a name that
 * happens to contain [suffix] earlier too (e.g. "ScreenshotScreen") keeps that part intact
 * ("Screenshot", not "hot"). No [suffix] configured (null/blank - the `screenSuffix` KSP option
 * wasn't set) means nothing is stripped at all.
 */
internal fun String.replaceScreenWord(suffix: String?): String {
    if (suffix.isNullOrEmpty()) return this
    val index = lastIndexOf(suffix, ignoreCase = true)
    if (index == -1) return this
    return removeRange(index, index + suffix.length)
}