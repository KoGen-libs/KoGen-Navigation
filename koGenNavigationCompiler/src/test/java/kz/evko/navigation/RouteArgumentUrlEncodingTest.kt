package kz.evko.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A route built the same way `RoutesListGenerator`/`ArgumentTypes` build one - `URLEncoder.encode`
 * every value going in, `URLDecoder.decode` it coming back out - round-trips exactly, even for a
 * value containing the query string's own separator characters.
 *
 * Found by actually navigating with a real link as a `String` argument, on an emulator: it never
 * crashed - the value used to go straight into the route with no encoding at all, so a `&` in it
 * silently truncated everything in the route after it (no exception, no log, just lost data) -
 * `?`/`/` happened to survive unencoded, but there was never a real guarantee of that. These tests
 * don't go through KSP/compile-testing at all (unlike [ScreenGeneratorArgumentTypesTest], which
 * covers that the *generated code* calls `URLEncoder`/`URLDecoder` in the first place) - they
 * execute the exact route-building/parsing shape those generators emit, directly, so a future
 * change that breaks the actual round trip (not just the generated text) fails here too.
 */
class RouteArgumentUrlEncodingTest {

    /** Builds a route the same way `RoutesListGenerator.generateRoutes()` does: `name?k1=<encoded>&k2=<encoded>`. */
    private fun buildRoute(name: String, vararg params: Pair<String, String>): String =
        params.joinToString(separator = "&", prefix = "$name?") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }

    /** Reads one query param back the same way `ArgumentTypes.getArgumentString`'s `String` branch does. */
    private fun readParam(route: String, key: String): String {
        val query = route.substringAfter("?")
        val raw = query.split("&").first { it.startsWith("$key=") }.substringAfter("=")
        return URLDecoder.decode(raw, "UTF-8")
    }

    @Test
    fun `a real link containing both a question mark and an ampersand survives the round trip intact`() {
        val value = "https://example.com/path?foo=1&bar=2"
        val route = buildRoute("link", "link" to value)
        assertEquals(value, readParam(route, "link"))
    }

    @Test
    fun `a bare ampersand survives the round trip intact`() {
        val value = "has&an-ampersand"
        val route = buildRoute("link", "link" to value)
        assertEquals(value, readParam(route, "link"))
    }

    @Test
    fun `an ampersand in one param's value doesn't corrupt a sibling param`() {
        val route = buildRoute("details", "title" to "a&b", "count" to "3")
        assertEquals("a&b", readParam(route, "title"))
        assertEquals("3", readParam(route, "count"))
    }

    @Test
    fun `a plain value with no special characters is unaffected`() {
        val value = "plain-no-special-chars"
        val route = buildRoute("link", "link" to value)
        assertEquals(value, readParam(route, "link"))
    }

    @Test
    fun `question marks and slashes round-trip cleanly too`() {
        // Neither actually needed encoding to survive even before this fix (only "&" - the query
        // separator itself - did), but there was never a real guarantee of that; worth locking in.
        val value = "has?a/question-and-slash"
        val route = buildRoute("link", "link" to value)
        assertEquals(value, readParam(route, "link"))
    }
}
