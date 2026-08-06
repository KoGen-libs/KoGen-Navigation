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
 */
data class ProcessorTestResult(
    val exitCode: KotlinCompilation.ExitCode,
    val messages: String,
    val generatedFiles: Map<String, String>,
) {
    fun generatedFile(name: String): String =
        generatedFiles[name]
            ?: error("No file named '$name' was generated. Generated files were: ${generatedFiles.keys}")
}

/**
 * Runs [ScreenGeneratorProcessorProvider] over [source] (a single Kotlin file's full text,
 * including its `package` declaration) with the given KSP [options] (mirrors the `ksp { arg(...) }`
 * block a consumer would configure).
 *
 * When [verifyCompiles] is true (the default), the generated Kotlin is compiled too - against a
 * small set of hand-written stand-ins for the AndroidX Compose/Navigation/Koin APIs it references
 * (see [androidStubSources]) - so a test fails not just on wrong *content* but on invalid Kotlin.
 */
fun compileScreens(
    source: String,
    options: Map<String, String> = emptyMap(),
    verifyCompiles: Boolean = true,
): ProcessorTestResult = compileScreenSources(
    SourceFile.kotlin("Screens.kt", source),
    options = options,
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

    val generatedFiles = compilation.kspSourcesDir
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .associate { it.name to it.readText() }

    return ProcessorTestResult(result.exitCode, result.messages, generatedFiles)
}
