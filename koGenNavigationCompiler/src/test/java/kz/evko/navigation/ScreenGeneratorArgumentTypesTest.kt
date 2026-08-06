package kz.evko.navigation

import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import kz.evko.navigation.testing.compileScreens
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers [kz.evko.navigation.contentGenerators.ArgumentTypes] end to end: for every supported
 * Kotlin type, the enum route's query string, the NavHost's `navArgument {}` block, and the
 * bundle-getter expression passed into the screen composable.
 */
class ScreenGeneratorArgumentTypesTest {

    @Test
    fun `scalar parameter types compile and wire up NavType, defaultValue and bundle getters`() {
        // Long is deliberately included and Float deliberately excluded here - see the
        // "KNOWN BUG" test below for why a Float parameter does not currently compile.
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(startDestination = true)
            @Composable
            fun DetailsScreen(
                flag: Boolean,
                name: String,
                count: Int,
                total: Long,
            ) {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)

        val screens = result.generatedFile("AppNavHostNavigationScreens.kt")
        assertTrue(
            screens.contains("Details(\"details?flag={flag}&name={name}&count={count}&total={total}\")"),
        )

        val navHost = result.generatedFile("AppNavHost.kt")
        assertTrue(navHost.contains("defaultValue = false"))
        assertTrue(navHost.contains("type = NavType.BoolType"))
        assertTrue(navHost.contains("defaultValue = \"\""))
        assertTrue(navHost.contains("type = NavType.StringType"))
        assertTrue(navHost.contains("defaultValue = 0"))
        assertTrue(navHost.contains("type = NavType.IntType"))
        assertTrue(navHost.contains("type = NavType.LongType"))

        assertTrue(navHost.contains("flag = it.arguments?.getBoolean(\"flag\") ?: false,"))
        assertTrue(navHost.contains("name = it.arguments?.getString(\"name\").orEmpty(),"))
        assertTrue(navHost.contains("count = it.arguments?.getInt(\"count\") ?: 0,"))
        assertTrue(navHost.contains("total = it.arguments?.getLong(\"total\") ?: 0,"))
    }

    @Test
    fun `KNOWN BUG - a non-null Float parameter generates a type mismatch and fails to compile`() {
        // ArgumentTypes.FloatType's bundle-getter fallback is "?: 0" (an Int literal), not "?: 0f".
        // Kotlin auto-widens an untyped int literal to Long (that's why the Long case above is
        // fine) but never to Float, so this is a hard compile error for any consumer with a
        // non-null Float route parameter. If this generator is fixed, update this test to assert
        // ExitCode.OK instead.
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(startDestination = true)
            @Composable
            fun DetailsScreen(ratio: Float) {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("but 'kotlin.Float' was expected"))
    }

    @Test
    fun `nullable parameter has null default and nullable=true, non-null keeps its type default`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(startDestination = true)
            @Composable
            fun DetailsScreen(id: String? = null, count: Int) {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)

        val navHost = result.generatedFile("AppNavHost.kt")
        assertTrue(
            navHost.contains(
                "navArgument(\"id\") {\n\t\t\t\t\tdefaultValue = null\n\t\t\t\t\ttype = NavType.StringType\n\t\t\t\t\tnullable = true",
            ),
        )
        assertTrue(
            navHost.contains(
                "navArgument(\"count\") {\n\t\t\t\t\tdefaultValue = 0\n\t\t\t\t\ttype = NavType.IntType\n\t\t\t\t\tnullable = false",
            ),
        )

        val routes = result.generatedFile("NavigationRoutes.kt")
        // A nullable route param whose *original screen function* parameter already had a
        // default value gets `= null` mirrored onto the generated Action's constructor too.
        assertTrue(routes.contains("id: String? = null,"))
        assertTrue(routes.contains("count: Int,"))
    }

    @Test
    fun `an unsupported custom type falls back to Gson serialization`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            data class UserProfile(val name: String)

            @KoGenScreen(startDestination = true)
            @Composable
            fun DetailsScreen(profile: UserProfile) {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)

        val navHost = result.generatedFile("AppNavHost.kt")
        assertTrue(
            navHost.contains(
                "profile = Gson().fromJson(it.arguments?.getString(\"profile\").orEmpty(), test.app.screens.UserProfile::class.java),",
            ),
        )

        val routes = result.generatedFile("NavigationRoutes.kt")
        assertTrue(routes.contains("profile: test.app.screens.UserProfile,"))
        assertTrue(routes.contains("profile=\${com.google.gson.Gson().toJson(profile)}"))
    }

    @Test
    fun `KNOWN BUG - a List parameter generates invalid Kotlin and fails to compile`() {
        // ArgumentTypes explicitly supports ListStringType (and the other List/Array variants),
        // but RoutesListGenerator prints the parameter type via the raw `KSTypeReference.toString()`
        // (`${param.type}`) instead of the resolved type - which renders as "List<INVARIANT String>",
        // not valid Kotlin. This pins the *current* broken behavior; if this generator is fixed,
        // update this test to assert ExitCode.OK instead.
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(startDestination = true)
            @Composable
            fun DetailsScreen(tags: List<String>) {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Unresolved reference 'INVARIANT'"))
    }

    @Test
    fun `array and list route query strings and bundle getters are generated (content only - see KNOWN BUG test)`() {
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            @KoGenScreen(startDestination = true)
            @Composable
            fun DetailsScreen(
                flags: BooleanArray,
                names: Array<String>,
                tags: List<String>,
                counts: IntArray,
                totals: LongArray,
                ratios: FloatArray,
            ) {
            }
            """.trimIndent(),
            verifyCompiles = false,
        )

        val navHost = result.generatedFile("AppNavHost.kt")
        assertTrue(navHost.contains("type = NavType.BoolArrayType"))
        assertTrue(navHost.contains("type = NavType.StringArrayType"))
        assertTrue(navHost.contains("type = NavType.IntArrayType"))
        assertTrue(navHost.contains("type = NavType.LongArrayType"))
        assertTrue(navHost.contains("type = NavType.FloatArrayType"))

        assertTrue(navHost.contains("flags = it.arguments?.getBooleanArray(\"flags\") ?: booleanArrayOf(),"))
        assertTrue(navHost.contains("names = it.arguments?.getStringArray(\"names\") ?: arrayOf(),"))
        assertTrue(navHost.contains("tags = it.arguments?.getStringArray(\"tags\")?.toList() ?: listOf(),"))
        assertTrue(navHost.contains("counts = it.arguments?.getIntArray(\"counts\") ?: intArrayOf(),"))
        assertTrue(navHost.contains("totals = it.arguments?.getLongArray(\"totals\") ?: longArrayOf(),"))
        assertTrue(navHost.contains("ratios = it.arguments?.getFloatArray(\"ratios\") ?: floatArrayOf(),"))
    }
}
