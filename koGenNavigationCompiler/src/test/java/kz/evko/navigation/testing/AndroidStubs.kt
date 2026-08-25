package kz.evko.navigation.testing

import com.tschuchort.compiletesting.SourceFile

/**
 * This compiler module has zero Android/Compose dependencies (it only emits Kotlin *source
 * text* referencing them). To actually compile the generated NavHost/Extensions files - and
 * catch real "unbalanced braces/wrong param name/bad type" regressions, not just string diffs -
 * we feed the compiler a minimal, hand-written stand-in for the handful of AndroidX/Compose/Koin
 * symbols the generated code touches. These are NOT meant to model real behavior, only real
 * shapes (names, parameter names, nullability) so that generated code type-checks the same way
 * it would against the real libraries.
 */
internal val androidStubSources: List<SourceFile> = listOf(
    SourceFile.kotlin(
        "AndroidLogStub.kt",
        """
        package android.util

        object Log {
            fun d(tag: String, msg: String): Int = 0
        }
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "LifecycleViewModelStub.kt",
        """
        package androidx.lifecycle

        abstract class ViewModel
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "ComposeRuntimeStub.kt",
        """
        package androidx.compose.runtime

        @Target(
            AnnotationTarget.FUNCTION,
            AnnotationTarget.TYPE,
            AnnotationTarget.TYPE_PARAMETER,
            AnnotationTarget.PROPERTY_GETTER,
        )
        annotation class Composable
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "ComposeUiStub.kt",
        """
        package androidx.compose.ui

        interface Modifier {
            companion object : Modifier
        }
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "ComposeUiUnitStub.kt",
        """
        package androidx.compose.ui.unit

        class IntOffset(val x: Int, val y: Int)

        // Stand-in for the implicit lambda receiver real slideIn/slideOut expose (which has a
        // `.width` property). Named/packaged arbitrarily since generated code never spells its
        // type out explicitly, only relies on it being inferred at the call site.
        class SizeLike(val width: Int, val height: Int)
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "ComposeAnimationStub.kt",
        """
        package androidx.compose.animation

        import androidx.compose.ui.unit.IntOffset
        import androidx.compose.ui.unit.SizeLike

        class EnterTransition
        class ExitTransition

        fun fadeIn(): EnterTransition = EnterTransition()
        fun fadeOut(): ExitTransition = ExitTransition()
        fun slideIn(initialOffset: (SizeLike) -> IntOffset): EnterTransition = EnterTransition()
        fun slideOut(targetOffset: (SizeLike) -> IntOffset): ExitTransition = ExitTransition()
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "ComposeUiTextIntlStub.kt",
        """
        package androidx.compose.ui.text.intl

        class Locale {
            companion object {
                val current: Locale = Locale()
            }
        }
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "ComposeUiTextStub.kt",
        """
        package androidx.compose.ui.text

        import androidx.compose.ui.text.intl.Locale

        fun String.capitalize(locale: Locale): String = this
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "NavigationStub.kt",
        """
        package androidx.navigation

        class Bundle {
            fun getBoolean(key: String): Boolean = false
            fun getString(key: String): String? = null
            fun getInt(key: String): Int = 0
            fun getLong(key: String): Long = 0L
            fun getFloat(key: String): Float = 0f
            fun getBooleanArray(key: String): BooleanArray? = null
            fun getStringArray(key: String): Array<String>? = null
            fun getIntArray(key: String): IntArray? = null
            fun getLongArray(key: String): LongArray? = null
            fun getFloatArray(key: String): FloatArray? = null
        }

        class SavedStateHandle {
            fun <T> get(key: String): T? = null
            fun <T> set(key: String, value: T) {}
            fun <T> remove(key: String): T? = null
        }

        open class NavDestination {
            val route: String? = null
            val id: Int = 0
        }

        class NavBackStackEntry {
            val arguments: Bundle? = null
            val destination: NavDestination = NavDestination()
            val savedStateHandle: SavedStateHandle = SavedStateHandle()
        }

        class NamedNavArgument
        class NavArgumentBuilder {
            var defaultValue: Any? = null
            var type: Any? = null
            var nullable: Boolean = false
        }

        fun navArgument(name: String, builder: NavArgumentBuilder.() -> Unit): NamedNavArgument =
            NamedNavArgument()

        class NavDeepLink
        class NavDeepLinkDslBuilder {
            var uriPattern: String? = null
        }

        fun navDeepLink(builder: NavDeepLinkDslBuilder.() -> Unit): NavDeepLink = NavDeepLink()

        object NavType {
            val BoolType: Any = Any()
            val StringType: Any = Any()
            val IntType: Any = Any()
            val LongType: Any = Any()
            val FloatType: Any = Any()
            val BoolArrayType: Any = Any()
            val StringArrayType: Any = Any()
            val IntArrayType: Any = Any()
            val LongArrayType: Any = Any()
            val FloatArrayType: Any = Any()
        }

        class PopUpToBuilder {
            var inclusive: Boolean = false
            var saveState: Boolean = false
        }

        class NavOptionsBuilder {
            var launchSingleTop: Boolean = false
            var restoreState: Boolean = false
            fun popUpTo(route: String, builder: PopUpToBuilder.() -> Unit) {}
            fun popUpTo(id: Int, builder: PopUpToBuilder.() -> Unit) {}
        }

        class NavGraph : NavDestination() {
            // Real shape: findStartDestination() lives in NavGraph's own companion, not as a
            // plain top-level function - a generator importing it the "obvious" wrong way
            // (as if it were top-level) fails silently against the real library; catch that here.
            companion object {
                fun NavGraph.findStartDestination(): NavDestination = this
            }
        }

        class NavHostController {
            val previousBackStackEntry: NavBackStackEntry? = null
            val currentBackStackEntry: NavBackStackEntry? = null
            val currentDestination: NavDestination? = null
            val graph: NavGraph = NavGraph()

            fun navigate(route: String, builder: NavOptionsBuilder.() -> Unit = {}) {}
            fun popBackStack() {}
        }

        // Real shape, not simplified away: composable(...) is an *extension* on NavGraphBuilder,
        // and NavHost's trailing lambda is a NavGraphBuilder receiver scope, not a plain () ->
        // Unit - matters for buildMode = "module", whose generated fun NavGraphBuilder.XxxGraph(...)
        // calls composable(...) bare, relying on it resolving against *its own* receiver, the same
        // way NavHost's own trailing lambda does. A simplified stub wouldn't catch a graph
        // extension that's broken in exactly that way.
        class NavGraphBuilder
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "NavigationComposeStub.kt",
        """
        package androidx.navigation.compose

        import androidx.compose.animation.EnterTransition
        import androidx.compose.animation.ExitTransition
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.unit.SizeLike
        import androidx.navigation.NamedNavArgument
        import androidx.navigation.NavBackStackEntry
        import androidx.navigation.NavDeepLink
        import androidx.navigation.NavGraphBuilder
        import androidx.navigation.NavHostController

        fun NavHost(
            modifier: Modifier = Modifier,
            navController: NavHostController,
            startDestination: String,
            builder: NavGraphBuilder.() -> Unit,
        ) {
        }

        fun NavGraphBuilder.navigation(
            startDestination: String,
            route: String,
            builder: NavGraphBuilder.() -> Unit,
        ) {
        }

        fun NavGraphBuilder.composable(
            route: String,
            arguments: List<NamedNavArgument> = emptyList(),
            deepLinks: List<NavDeepLink> = emptyList(),
            enterTransition: ((SizeLike) -> EnterTransition?)? = null,
            exitTransition: ((SizeLike) -> ExitTransition?)? = null,
            popEnterTransition: ((SizeLike) -> EnterTransition?)? = null,
            popExitTransition: ((SizeLike) -> ExitTransition?)? = null,
            content: @Composable (NavBackStackEntry) -> Unit,
        ) {
        }
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "KoinComposeStub.kt",
        """
        package org.koin.androidx.compose

        inline fun <reified T> koinViewModel(): T = TODO("stub")
        """.trimIndent(),
    ),
    SourceFile.kotlin(
        "LifecycleViewModelComposeStub.kt",
        """
        package androidx.lifecycle.viewmodel.compose

        inline fun <reified T> viewModel(): T = TODO("stub")
        """.trimIndent(),
    ),
)
