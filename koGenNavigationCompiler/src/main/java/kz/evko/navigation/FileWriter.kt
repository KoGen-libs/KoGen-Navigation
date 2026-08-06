package kz.evko.navigation

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import kz.evko.navigation.contentGenerators.NavHostContentGenerator
import kz.evko.navigation.contentGenerators.RoutesListGenerator
import kz.evko.navigation.contentGenerators.ScreenListGenerator
import kz.evko.navigation.helpers.NavigationAnimation
import kz.evko.navigation.helpers.ViewModelInjector
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
            packageParts?.take(3)?.joinToString(".")?.plus(".navigation")
                ?: "kz.evko.navigation"
        }
    }

    fun createScreensList(
        screensFunctions: List<KSFunctionDeclaration>,
        name: String,
        viewModelInjector: ViewModelInjector,
        defaultAnimation: NavigationAnimation,
        screenSuffix: String?,
    ) {
        if (!screensFunctions.iterator().hasNext()) return

        try {
            val fileName = "${name}NavigationScreens"
            val screenListContentGenerator = ScreenListGenerator(packageName, screenSuffix)
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
                screenSuffix = screenSuffix,
            )
        } catch (e: Exception) {
            logger.info("Exception: ${e.message}")
        }
    }

    private fun createNavHost(
        screensFunctions: List<KSFunctionDeclaration>, name: String,
        viewModelInjector: ViewModelInjector,
        defaultAnimation: NavigationAnimation,
        screenSuffix: String?,
    ) {
        val navHostContentGenerator = NavHostContentGenerator(packageName, screenSuffix)
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

    fun createRoutes(screensFunctions: List<KSFunctionDeclaration>, screenSuffix: String?) {
        try {
            val routesContentGenerator = RoutesListGenerator(packageName, screenSuffix)

            val routesContent = routesContentGenerator.generateRoutes(
                screensFunctions.toList(),
            )

            val routesFile: OutputStream =
                createFile(screensFunctions.toFileList(), "NavigationRoutes")
            routesFile += routesContent
            routesFile.close()
        } catch (e: Exception) {
            logger.info("Exception: ${e.message}")
        }
    }

    fun createExtensions() {
        try {
            // No screen name to strip a suffix from here - generateExtensions() doesn't use it.
            val routesContentGenerator = RoutesListGenerator(packageName, screenSuffix = null)
            val extensionsFile: OutputStream =
                createFile(emptyList(), "NavigationExtensions")
            extensionsFile += routesContentGenerator.generateExtensions()
            extensionsFile.close()
        } catch (e: Exception) {
            logger.info("Exception: ${e.message}")
        }
    }

    private fun createFile(
        files: List<KSFile>,
        fileName: String,
    ) = codeGenerator.createNewFile(
        Dependencies(
            true,
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