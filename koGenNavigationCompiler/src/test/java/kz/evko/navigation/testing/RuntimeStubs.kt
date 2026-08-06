package kz.evko.navigation.testing

import com.tschuchort.compiletesting.SourceFile

/**
 * Stand-ins for the `kz.evko.navigation.routes`/`kz.evko.navigation.helpers` runtime types
 * (`NavigationAction`, `RouteScreenType`, `BackStackData`, `NavigationResultKey`, ...).
 *
 * These normally live in `:koGenNavigation` (an Android library), which a plain JVM module like
 * `:koGenNavigationCompiler` cannot depend on (no matching Gradle variant - see the module's
 * README/history for why). The compiler never actually imports these as real Kotlin types either
 * - it only ever prints their fully-qualified names as *text* into generated files - so mirroring
 * their exact shape here is enough to compile-verify the generated code end to end without a
 * cross-variant project dependency.
 *
 * Keep these in sync with the real declarations in
 * `koGenNavigation/src/main/java/kz/evko/navigation/routes/NavigationAction.kt` and
 * `.../helpers/NavigationResult.kt`.
 */
internal val runtimeStubSources: List<SourceFile> = listOf(
    SourceFile.kotlin(
        "NavigationActionStub.kt",
        """
        package kz.evko.navigation.routes

        interface RouteScreenType {
            val route: String
        }

        open class NavigationAction(
            val route: String,
        )

        fun NavigationAction.navigationLog(
            popUpTo: RouteScreenType? = null,
            inclusive: Boolean = false,
        ): String = ""

        fun navigationBackLog(
            fromScreen: String?,
            toScreen: String?,
        ): String = ""
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "NavigationResultStub.kt",
        """
        package kz.evko.navigation.helpers

        data class BackStackData<T>(
            val data: NavigationResultKey<T>,
            val value: T,
        )

        interface NavigationResultKey<T> {
            val defaultValue: T?
            val key: String
        }
        """.trimIndent(),
    ),
)
