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
                ratio: Float,
            ) {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)

        val screens = result.generatedFile("AppNavHostNavigationScreens.kt")
        assertTrue(
            screens.contains("Details(\"details?flag={flag}&name={name}&count={count}&total={total}&ratio={ratio}\")"),
        )

        val navHost = result.generatedFile("AppNavHost.kt")
        assertTrue(navHost.contains("defaultValue = false"))
        assertTrue(navHost.contains("type = NavType.BoolType"))
        assertTrue(navHost.contains("defaultValue = \"\""))
        assertTrue(navHost.contains("type = NavType.StringType"))
        assertTrue(navHost.contains("defaultValue = 0"))
        assertTrue(navHost.contains("type = NavType.IntType"))
        assertTrue(navHost.contains("type = NavType.LongType"))
        assertTrue(navHost.contains("type = NavType.FloatType"))

        assertTrue(navHost.contains("flag = it.arguments?.getBoolean(\"flag\") ?: false,"))
        assertTrue(navHost.contains("name = it.arguments?.getString(\"name\").orEmpty(),"))
        assertTrue(navHost.contains("count = it.arguments?.getInt(\"count\") ?: 0,"))
        assertTrue(navHost.contains("total = it.arguments?.getLong(\"total\") ?: 0,"))
        // Regression check: this used to generate "?: 0" (an untyped Int literal), which fails to
        // compile because Kotlin never auto-widens Int to Float (unlike Int-to-Long).
        assertTrue(navHost.contains("ratio = it.arguments?.getFloat(\"ratio\") ?: 0f,"))
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
                "profile = it.arguments?.getString(\"profile\").orEmpty().let { json -> Gson().fromJson<test.app.screens.UserProfile>" +
                    "(json, object : com.google.gson.reflect.TypeToken<test.app.screens.UserProfile>() {}.type) },",
            ),
        )

        val routes = result.generatedFile("NavigationRoutes.kt")
        assertTrue(routes.contains("profile: test.app.screens.UserProfile,"))
        assertTrue(routes.contains("profile=\${com.google.gson.Gson().toJson(profile)}"))
    }

    @Test
    fun `a nullable custom type is deserialized null-safely instead of crashing on a missing value`() {
        // Regression check: the Gson fallback used to always call `.orEmpty()` then parse,
        // regardless of nullability - a missing/absent nullable custom-type argument would throw
        // JsonSyntaxException trying to parse an empty string, instead of just being null.
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            data class UserProfile(val name: String)

            @KoGenScreen(startDestination = true)
            @Composable
            fun DetailsScreen(profile: UserProfile?) {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)

        val navHost = result.generatedFile("AppNavHost.kt")
        assertTrue(
            navHost.contains(
                "profile = it.arguments?.getString(\"profile\")?.let { json -> Gson().fromJson<test.app.screens.UserProfile>" +
                    "(json, object : com.google.gson.reflect.TypeToken<test.app.screens.UserProfile>() {}.type) },",
            ),
        )

        val routes = result.generatedFile("NavigationRoutes.kt")
        assertTrue(routes.contains("profile: test.app.screens.UserProfile?,"))
    }

    @Test
    fun `a List of a custom type is deserialized via TypeToken with a fully-qualified type argument`() {
        // Regression check: this used to generate `kotlin.collections.List<UserProfile>::class.java`
        // (invalid: a bare Class reference can't express a parameterized generic type, and
        // "UserProfile" itself was left unqualified/unresolved since only the screen function
        // ever gets imported).
        val result = compileScreens(
            """
            package test.app.screens

            import androidx.compose.runtime.Composable
            import kz.evko.navigation.annotation.KoGenScreen

            data class UserProfile(val name: String)

            @KoGenScreen(startDestination = true)
            @Composable
            fun DetailsScreen(profiles: List<UserProfile>) {
            }
            """.trimIndent(),
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)

        val navHost = result.generatedFile("AppNavHost.kt")
        assertTrue(
            navHost.contains(
                "profiles = it.arguments?.getString(\"profiles\").orEmpty().let { json -> Gson().fromJson<kotlin.collections.List<test.app.screens.UserProfile>>" +
                    "(json, object : com.google.gson.reflect.TypeToken<kotlin.collections.List<test.app.screens.UserProfile>>() {}.type) },",
            ),
        )

        val routes = result.generatedFile("NavigationRoutes.kt")
        assertTrue(routes.contains("profiles: kotlin.collections.List<test.app.screens.UserProfile>,"))
    }

    @Test
    fun `array and list parameters compile and wire up NavType and bundle getters`() {
        // Regression check: RoutesListGenerator used to print these parameter types via the raw,
        // as-written KSTypeReference ("List<INVARIANT String>", not valid Kotlin) instead of the
        // resolved type. See NavigationRoutes.kt's assertion below.
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
        )

        assertEquals(ExitCode.OK, result.exitCode, result.messages)

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

        val routes = result.generatedFile("NavigationRoutes.kt")
        assertTrue(routes.contains("tags: List<String>,"))
        assertTrue(routes.contains("names: Array<String>,"))
    }
}
