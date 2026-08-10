package kz.evko.navigation

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.writeTo
import kz.evko.navigation.contentGenerators.NavHostContentGenerator
import kz.evko.navigation.contentGenerators.RoutesListGenerator
import kz.evko.navigation.contentGenerators.ScreenListGenerator
import kz.evko.navigation.helpers.NavigationAnimation
import kz.evko.navigation.helpers.ViewModelInjector
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

    /** Writes `<name>NavigationScreens.kt` for one `navHostName` group, then its `NavHost` via [createNavHost]. No-ops for an empty group. */
    fun createScreensList(
        screensFunctions: List<KSFunctionDeclaration>,
        name: String,
        viewModelInjector: ViewModelInjector,
        defaultAnimation: NavigationAnimation,
        screenSuffix: String?,
    ) {
        if (!screensFunctions.iterator().hasNext()) return

        val fileName = "${name}NavigationScreens"
        val screenListContentGenerator = ScreenListGenerator(packageName, screenSuffix)
        val fileSpec = screenListContentGenerator.generateScreenList(
            screensFunctions.toList(),
            fileName,
            logger,
        )
        fileSpec.writeToGenerated(screensFunctions)

        createNavHost(
            screensFunctions = screensFunctions,
            name = name,
            viewModelInjector = viewModelInjector,
            defaultAnimation = defaultAnimation,
            screenSuffix = screenSuffix,
        )
    }

    /** Writes `<name>.kt`, the `@Composable fun <name>(...)` `NavHost` for this group of screens. */
    private fun createNavHost(
        screensFunctions: List<KSFunctionDeclaration>, name: String,
        viewModelInjector: ViewModelInjector,
        defaultAnimation: NavigationAnimation,
        screenSuffix: String?,
    ) {
        val navHostContentGenerator = NavHostContentGenerator(packageName, screenSuffix, logger)
        val fileSpec = navHostContentGenerator.generateNavHost(
            functionList = screensFunctions.toList(),
            hostName = name,
            viewModelInjector = viewModelInjector,
            defaultAnimation = defaultAnimation,
        )
        fileSpec.writeToGenerated(screensFunctions)
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