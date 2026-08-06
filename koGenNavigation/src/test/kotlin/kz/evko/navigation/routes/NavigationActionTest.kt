package kz.evko.navigation.routes

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class HomeAction : NavigationAction(route = "home")
private class DetailsAction(id: String) : NavigationAction(route = "details?id=$id")
private object HomeScreen : RouteScreenType {
    override val route: String = "home"
    override fun toString(): String = "Home"
}

class NavigationActionTest {

    @Test
    fun `logs the action's simple class name`() {
        val log = HomeAction().navigationLog()

        assertTrue(log.contains("Navigation action: HomeAction"))
    }

    @Test
    fun `plain routes without query params don't print a params line`() {
        val log = HomeAction().navigationLog()

        // Only one line about the action itself, no extra "| ..." line derived from splitting the route.
        assertFalse(log.lines().any { it.trim() == "| home" })
    }

    @Test
    fun `routes with query params print the params part after the question mark`() {
        val log = DetailsAction(id = "42").navigationLog()

        assertTrue(log.contains("| id=42"))
    }

    @Test
    fun `omits popUpTo line when none is given`() {
        val log = HomeAction().navigationLog()

        assertFalse(log.contains("popUpTo"))
    }

    @Test
    fun `includes popUpTo line when a target is given`() {
        val log = HomeAction().navigationLog(popUpTo = HomeScreen)

        assertTrue(log.contains("| popUpTo: Home"))
    }

    @Test
    fun `omits inclusive line by default`() {
        val log = HomeAction().navigationLog()

        assertFalse(log.contains("is inclusive"))
    }

    @Test
    fun `includes inclusive line when inclusive is true`() {
        val log = HomeAction().navigationLog(inclusive = true)

        assertTrue(log.contains("is inclusive"))
    }

    @Test
    fun `back log reports both screen names`() {
        val log = navigationBackLog(fromScreen = "Home", toScreen = "Details")

        assertTrue(log.contains("Home -> Details"))
    }

    @Test
    fun `back log tolerates missing screen names`() {
        val log = navigationBackLog(fromScreen = null, toScreen = null)

        assertTrue(log.contains("null -> null"))
    }
}
