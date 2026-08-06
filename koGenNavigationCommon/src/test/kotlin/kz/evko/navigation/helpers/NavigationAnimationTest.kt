package kz.evko.navigation.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Locks in the exact Kotlin source snippets [AnimationType.buildAnimationContent] emits.
 * These strings get spliced verbatim into a generated NavHost's `composable(...) { }` block,
 * so any change here changes what every consumer's generated navigation code looks like.
 */
class NavigationAnimationTest {

    @Test
    fun `None produces no transition code`() {
        assertEquals("", AnimationType.None.buildAnimationContent())
    }

    @Test
    fun `Fade emits fadeIn and fadeOut transitions`() {
        val content = AnimationType.Fade.buildAnimationContent()

        assertTrue(content.contains("enterTransition = { androidx.compose.animation.fadeIn() },"))
        assertTrue(content.contains("exitTransition = { androidx.compose.animation.fadeOut() },"))
    }

    @Nested
    @DisplayName("Slide directions")
    inner class SlideDirections {

        @Test
        fun `SlideLeft slides content in from the right and out to the left`() {
            val content = NavigationAnimation.SlideLeft.type.buildAnimationContent()

            assertTrue(content.contains("enterTransition"))
            assertOffset(content, "enterTransition", "it.width", "0")
            assertOffset(content, "exitTransition", "-it.width", "0")
            assertOffset(content, "popEnterTransition", "-it.width", "0")
            assertOffset(content, "popExitTransition", "it.width", "0")
        }

        @Test
        fun `SlideRight slides content in from the left and out to the right`() {
            val content = NavigationAnimation.SlideRight.type.buildAnimationContent()

            assertOffset(content, "enterTransition", "-it.width", "0")
            assertOffset(content, "exitTransition", "it.width", "0")
            assertOffset(content, "popEnterTransition", "it.width", "0")
            assertOffset(content, "popExitTransition", "-it.width", "0")
        }

        @Test
        fun `SlideUp slides content in from the bottom and out to the top`() {
            val content = NavigationAnimation.SlideUp.type.buildAnimationContent()

            assertOffset(content, "enterTransition", "0", "it.width")
            assertOffset(content, "exitTransition", "0", "-it.width")
            assertOffset(content, "popEnterTransition", "0", "-it.width")
            assertOffset(content, "popExitTransition", "0", "it.width")
        }

        @Test
        fun `SlideDown slides content in from the top and out to the bottom`() {
            val content = NavigationAnimation.SlideDown.type.buildAnimationContent()

            assertOffset(content, "enterTransition", "0", "-it.width")
            assertOffset(content, "exitTransition", "0", "it.width")
            assertOffset(content, "popEnterTransition", "0", "it.width")
            assertOffset(content, "popExitTransition", "0", "-it.width")
        }
    }

    /**
     * Asserts that the named transition block (e.g. "enterTransition") builds an
     * `IntOffset(x, y)` with the expected literal x/y expressions. Note: both axes are rendered
     * with `it.width` (never `it.height`) - that's the current, intentional-or-not behavior of
     * [AnimationType.getOffset], and this test pins it down so a refactor doesn't silently change
     * vertical slide distances.
     */
    private fun assertOffset(content: String, transitionName: String, x: String, y: String) {
        val pattern = Regex(
            "$transitionName = \\{[\\s\\S]*?IntOffset\\(${Regex.escape(x)}, ${Regex.escape(y)}\\)"
        )
        assertTrue(
            pattern.containsMatchIn(content),
            "Expected $transitionName to build IntOffset($x, $y) but was:\n$content",
        )
    }
}
