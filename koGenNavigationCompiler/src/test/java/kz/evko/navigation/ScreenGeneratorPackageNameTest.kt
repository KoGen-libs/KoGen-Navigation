package kz.evko.navigation

import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import kz.evko.navigation.testing.compileScreens
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers `FileWriter.createPackageName`'s resolution order: an explicit `packageName` KSP option
 * wins; otherwise it's inferred from the first annotated screen's own package.
 */
class ScreenGeneratorPackageNameTest {

    @Test
    fun `an explicit packageName option is used verbatim, with a  navigation suffix`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(startDestination = true)
            @Composable
            fun HomeScreen() {
            }
            """.trimIndent(),
            options = mapOf("packageName" to "com.example.myapp"),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertTrue(result.generatedFile("AppNavHost.kt").startsWith("package com.example.myapp.navigation"))
    }

    @Test
    fun `with no explicit packageName, it's inferred from the first screen's own package`() {
        val result = compileScreens(
            """
            package com.example.myapp.ui.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(startDestination = true)
            @Composable
            fun HomeScreen() {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        // Only the first 3 package segments are kept.
        assertTrue(result.generatedFile("AppNavHost.kt").startsWith("package com.example.myapp.navigation"))
    }

    @Test
    fun `inference no longer crashes when the screen's package has fewer than 3 segments`() {
        // Regression test for a fixed bug: createPackageName used to do
        // `packageParts.subList(0, 3)` unconditionally, which threw IndexOutOfBoundsException for
        // any screen declared in a 1- or 2-segment package. It now takes at most 3 segments.
        val result = compileScreens(
            """
            package app

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(startDestination = true)
            @Composable
            fun HomeScreen() {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertTrue(result.generatedFile("AppNavHost.kt").startsWith("package app.navigation"))
    }

    @Test
    fun `with no explicit packageName, NavigationExtensions is written exactly once, not duplicated`() {
        // Regression test for a fixed bug: KSP runs this processor across several rounds per
        // compilation (observed: 3) even when nothing changed. createPackageName()/
        // createExtensions() used to re-run unconditionally on every round; by round 2 there were
        // no annotated symbols left to infer a package from, so the *inferred* case silently fell
        // back to the hardcoded default and wrote a second, stray NavigationExtensions.kt under
        // kz.evko.navigation - alongside the correctly-inferred one. Only manifested when
        // packageName wasn't set explicitly (an explicit packageName resolves the same way on
        // every round, so there was nothing to overwrite it with).
        val result = compileScreens(
            """
            package com.example.myapp.ui.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(startDestination = true)
            @Composable
            fun HomeScreen() {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)
        assertEquals(1, result.countOf("NavigationExtensions.kt"))
        assertTrue(result.generatedFile("NavigationExtensions.kt").startsWith("package com.example.myapp.navigation"))
    }
}
