package kz.evko.navigation.testing

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspSourcesDir
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kz.evko.navigation.ScreenGeneratorProcessorProvider

/**
 * Result of running [ScreenGeneratorProcessorProvider] over a set of sources.
 *
 * @param exitCode outcome of the *full* compilation (KSP round + compiling everything it
 *   generated). Only meaningful when the call site asked to `verifyCompiles`.
 * @param generatedFiles generated file name -> its full source text, e.g. "NavigationRoutes.kt".
 *   Deduplicated by file name: if KSP wrote the same file name under more than one package/path
 *   (a real bug we've hit before), only the last one survives here - use [generatedFilePaths] to
 *   detect that.
 * @param generatedFilePaths every generated file's absolute path, undeduplicated - its count for
 *   a given file name is how many times that name was (re)written, possibly to different packages.
 */
data class ProcessorTestResult(
    val exitCode: KotlinCompilation.ExitCode,
    val messages: String,
    val generatedFiles: Map<String, String>,
    val generatedFilePaths: List<String>,
) {
    fun generatedFile(name: String): String =
        generatedFiles[name]
            ?: error("No file named '$name' was generated. Generated files were: ${generatedFiles.keys}")

    /** How many times a file named [name] was written, regardless of which package it ended up in. */
    fun countOf(name: String): Int = generatedFilePaths.count { it.endsWith("/$name") }
}

/**
 * Runs [ScreenGeneratorProcessorProvider] over [source] (a single Kotlin file's full text,
 * including its `package` declaration) with the given KSP [options] (mirrors the `ksp { arg(...) }`
 * block a consumer would configure).
 *
 * [options] is merged on top of [defaultTestOptions] (caller-supplied values win on key
 * collision) so most tests - which only care about *one* specific option - don't all need to
 * repeat `"screenSuffix" to "Screen"` just to keep their unrelated name assertions ("Home", not
 * "HomeScreen") working. Tests that specifically exercise `screenSuffix` itself should pass it
 * explicitly (including `"screenSuffix" to ""` to test the "unset" behavior).
 *
 * When [verifyCompiles] is true (the default), the generated Kotlin is compiled too - against a
 * small set of hand-written stand-ins for the AndroidX Compose/Navigation/Koin APIs it references
 * (see [androidStubSources]) - so a test fails not just on wrong *content* but on invalid Kotlin.
 */
val defaultTestOptions = mapOf("screenSuffix" to "Screen")

fun compileScreens(
    source: String,
    options: Map<String, String> = emptyMap(),
    verifyCompiles: Boolean = true,
): ProcessorTestResult = compileScreenSources(
    SourceFile.kotlin("Screens.kt", source),
    options = defaultTestOptions + options,
    verifyCompiles = verifyCompiles,
)

@OptIn(ExperimentalCompilerApi::class)
fun compileScreenSources(
    vararg testSources: SourceFile,
    options: Map<String, String> = emptyMap(),
    verifyCompiles: Boolean = true,
): ProcessorTestResult {
    val compilation = KotlinCompilation().apply {
        sources = testSources.toList() + androidStubSources + runtimeStubSources
        inheritClassPath = true
        messageOutputStream = System.out

        configureKsp(useKsp2 = true) {
            symbolProcessorProviders += ScreenGeneratorProcessorProvider()
            processorOptions.putAll(options)
            withCompilation = verifyCompiles
        }
    }

    val result = compilation.compile()

    val allKtFiles = compilation.kspSourcesDir
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    val generatedFiles = allKtFiles.associate { it.name to it.readText() }
    val generatedFilePaths = allKtFiles.map { it.absolutePath }

    return ProcessorTestResult(result.exitCode, result.messages, generatedFiles, generatedFilePaths)
}
