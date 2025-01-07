package kz.evko.processor.annotation

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

    fun buildAnimationContent() = when (this) {
        None -> ""
        Fade -> buildString {
            appendLine("\t\t\tenterTransition = { androidx.compose.animation.fadeIn() },")
            appendLine("\t\t\texitTransition = { androidx.compose.animation.fadeOut() },")
        }

        is Slide -> buildString {
            appendLine(buildSlideInAnimation("enterTransition", enter))
            appendLine(buildSlideOutAnimation("exitTransition", exit))
            appendLine(buildSlideInAnimation("popEnterTransition", popEnter))
            appendLine(buildSlideOutAnimation("popExitTransition", popExit))
        }
    }

    private fun buildSlideInAnimation(transitionName: String, offset: Offset) = buildString {
        appendLine("\t\t\t$transitionName = {")
        appendLine("\t\t\t\tandroidx.compose.animation.slideIn(")
        appendLine("\t\t\t\t\tinitialOffset = {")
        appendLine(
            "\t\t\t\t\t\tandroidx.compose.ui.unit.IntOffset(${getOffset(offset.x)}, ${
                getOffset(
                    offset.y
                )
            })"
        )
        appendLine("\t\t\t\t\t}")
        appendLine("\t\t\t\t)")
        appendLine("\t\t\t},")
    }

    private fun buildSlideOutAnimation(transitionName: String, offset: Offset) = buildString {
        appendLine("\t\t\t$transitionName = {")
        appendLine("\t\t\t\tandroidx.compose.animation.slideOut(")
        appendLine("\t\t\t\t\ttargetOffset = {")
        appendLine(
            "\t\t\t\t\t\tandroidx.compose.ui.unit.IntOffset(${getOffset(offset.x)}, ${
                getOffset(
                    offset.y
                )
            })"
        )
        appendLine("\t\t\t\t\t}")
        appendLine("\t\t\t\t)")
        appendLine("\t\t\t},")
    }

    private fun getOffset(value: Float) =
        when {
            value > 0f -> "it.width"
            value < 0f -> "-it.width"
            else -> "0"
        }

}

class Offset(val x: Float, val y: Float)