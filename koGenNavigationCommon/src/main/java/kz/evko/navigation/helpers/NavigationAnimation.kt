package kz.evko.navigation.helpers

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

sealed class AnimationType {
    data object None : AnimationType()
    data object Fade : AnimationType()
    class Slide(
        val enter: Offset,
        val exit: Offset,
        val popEnter: Offset,
        val popExit: Offset,
    ) : AnimationType()

    // No absolute leading tabs here: whichever generator embeds this text (currently
    // NavHostContentGenerator, via KotlinPoet's `%L`) already applies the correct indentation to
    // every line of it for wherever it's embedded - hardcoding our own absolute tabs on top just
    // doubled up on it. Relative tabs, starting from 0, are enough to keep the nesting readable.
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

class Offset(val x: Float, val y: Float)