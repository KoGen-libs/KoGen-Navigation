package kz.evko.navigation

import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import kz.evko.navigation.testing.compileScreens
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers `FileWriter.createPackageName`'s resolution order: an explicit `packageName` KSP option
 * wins; otherwise it's inferred from the first annotated screen's own package.
 *
 * Note: `NavigationExtensions.kt` is *not* asserted on here. KSP runs this processor across
 * several rounds per compilation (observed: 3) even when nothing changed; `createPackageName` +
 * `createExtensions()` re-run unconditionally on every round, and by round 2 there are no
 * annotated symbols left to infer a package from, so the *inferred* case silently falls back to
 * the hardcoded default and overwrites `NavigationExtensions.kt` under `kz.evko.navigation`
 * instead of the real inferred package - see the KNOWN BUG test below. AppNavHost.kt/the Screens
 * enum/NavigationRoutes.kt are unaffected because they're only (re)written while there are
 * annotated screens to process.
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
    fun `KNOWN BUG - inference crashes when the screen's package has fewer than 3 segments`() {
        // FileWriter.createPackageName does `packageParts.subList(0, 3)` unconditionally when no
        // packageName option is given. Any screen declared in a 1- or 2-segment package (or the
        // default/root package) crashes the whole build with an IndexOutOfBoundsException instead
        // of falling back gracefully. Passing an explicit `packageName` option is the only current
        // workaround. If this is fixed, update this test to assert ExitCode.OK instead.
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
            verifyCompiles = false,
        )

        assertTrue(result.messages.contains("IndexOutOfBoundsException"))
    }
}
