package kz.evko.navigation.helpers

/**
 * Enter/exit transition for a generated `composable(...)` entry. Set per-screen via
 * `@KoGenScreen(animation = ...)`, or project-wide as the fallback via the `defaultAnimation` KSP
 * option, which is matched against [typeName] (e.g. `ksp { arg("defaultAnimation", "slideLeft") }`).
 *
 * [type] carries the actual Compose transition code to emit for this entry - see [AnimationType].
 */
enum class NavigationAnimation(
    val typeName: String,
    val type: AnimationType,
) {
    None("none", AnimationType.None),
    Fade("fade", AnimationType.Fade),
    SlideLeft(
        "slideLeft",
        AnimationType.Slide(
            enter = Offset(1f, 0f),
            exit = Offset(-1f, 0f),
            popEnter = Offset(-1f, 0f),
            popExit = Offset(1f, 0f),
        )
    ),
    SlideRight(
        "slideRight",
        AnimationType.Slide(
            enter = Offset(-1f, 0f),
            exit = Offset(1f, 0f),
            popEnter = Offset(1f, 0f),
            popExit = Offset(-1f, 0f),
        )
    ),
    SlideUp(
        "slideUp",
        AnimationType.Slide(
            enter = Offset(0f, 1f),
            exit = Offset(0f, -1f),
            popEnter = Offset(0f, -1f),
            popExit = Offset(0f, 1f),
        )
    ),
    SlideDown(
        "slideDown",
        AnimationType.Slide(
            enter = Offset(0f, -1f),
            exit = Offset(0f, 1f),
            popEnter = Offset(0f, 1f),
            popExit = Offset(0f, -1f),
        )
    ),
}

/**
 * The Compose transition to render as source code for a `composable(...)` entry's
 * `enterTransition`/`exitTransition`/`popEnterTransition`/`popExitTransition` arguments.
 * [buildAnimationContent] does the actual rendering; [NavigationAnimation] maps the
 * user/KSP-option-facing name to one of these.
 */
sealed class AnimationType {
    /** No transition arguments at all - `composable(...)` falls back to its own defaults. */
    data object None : AnimationType()

    /** Cross-fade in/out, same on the way back. */
    data object Fade : AnimationType()

    /** Directional slide, with independent offsets for the forward and the back-stack-pop direction. */
    class Slide(
        val enter: Offset,
        val exit: Offset,
        val popEnter: Offset,
        val popExit: Offset,
    ) : AnimationType()

    /**
     * Renders this transition as the literal Kotlin source text of the four
     * `*Transition = { ... }` lambda arguments (empty string for [None]).
     *
     * No absolute leading tabs here: whichever generator embeds this text (currently
     * NavHostContentGenerator, via KotlinPoet's `%L`) already applies the correct indentation to
     * every line of it for wherever it's embedded - hardcoding our own absolute tabs on top just
     * doubled up on it. Relative tabs, starting from 0, are enough to keep the nesting readable.
     */
    fun buildAnimationContent() = when (this) {
        None -> ""
        Fade -> buildString {
            appendLine("enterTransition = { androidx.compose.animation.fadeIn() },")
            appendLine("exitTransition = { androidx.compose.animation.fadeOut() },")
        }

        is Slide -> buildString {
            appendLine(buildSlideInAnimation("enterTransition", enter))
            appendLine(buildSlideOutAnimation("exitTransition", exit))
            appendLine(buildSlideInAnimation("popEnterTransition", popEnter))
            appendLine(buildSlideOutAnimation("popExitTransition", popExit))
        }
    }

    private fun buildSlideInAnimation(transitionName: String, offset: Offset) = buildString {
        appendLine("$transitionName = {")
        appendLine("\tandroidx.compose.animation.slideIn(")
        appendLine("\t\tinitialOffset = {")
        appendLine(
            "\t\t\tandroidx.compose.ui.unit.IntOffset(${getOffset(offset.x)}, ${
                getOffset(
                    offset.y
                )
            })"
        )
        appendLine("\t\t}")
        appendLine("\t)")
        appendLine("},")
    }

    private fun buildSlideOutAnimation(transitionName: String, offset: Offset) = buildString {
        appendLine("$transitionName = {")
        appendLine("\tandroidx.compose.animation.slideOut(")
        appendLine("\t\ttargetOffset = {")
        appendLine(
            "\t\t\tandroidx.compose.ui.unit.IntOffset(${getOffset(offset.x)}, ${
                getOffset(
                    offset.y
                )
            })"
        )
        appendLine("\t\t}")
        appendLine("\t)")
        appendLine("},")
    }

    private fun getOffset(value: Float) =
        when {
            value > 0f -> "it.width"
            value < 0f -> "-it.width"
            else -> "0"
        }

}

/** A slide direction/distance as a unit vector: e.g. `Offset(1f, 0f)` slides in from the right. */
class Offset(val x: Float, val y: Float)