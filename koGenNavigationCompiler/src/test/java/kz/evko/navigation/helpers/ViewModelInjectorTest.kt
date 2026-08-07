package kz.evko.navigation.helpers

import com.squareup.kotlinpoet.MemberName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * [ViewModelInjector.diName] is read straight from the `viewModelInjector` KSP option
 * (see ScreenGeneratorProcessor). [ViewModelInjector.injectorFunction] is spliced into the
 * generated NavHost via KotlinPoet's `%M`/`%T` - it's a real [MemberName], not a raw import
 * string, so any change here changes generated code (and its imports) for every consumer.
 */
class ViewModelInjectorTest {

    @Test
    fun `diName selects Koin from the koin option`() {
        assertEquals(
            ViewModelInjector.Koin,
            ViewModelInjector.entries.firstOrNull { it.diName == "koin" },
        )
    }

    @Test
    fun `diName selects Hilt from the hilt option`() {
        assertEquals(
            ViewModelInjector.Hilt,
            ViewModelInjector.entries.firstOrNull { it.diName == "hilt" },
        )
    }

    @Test
    fun `unknown option resolves to no match, caller falls back to None`() {
        assertEquals(null, ViewModelInjector.entries.firstOrNull { it.diName == "dagger" })
    }

    @Test
    fun `Koin injects via koinViewModel`() {
        assertEquals(
            MemberName("org.koin.androidx.compose", "koinViewModel"),
            ViewModelInjector.Koin.injectorFunction,
        )
    }

    @Test
    fun `Hilt injects via the generic viewModel() composable`() {
        assertEquals(
            MemberName("androidx.lifecycle.viewmodel.compose", "viewModel"),
            ViewModelInjector.Hilt.injectorFunction,
        )
    }

    @Test
    fun `None has no injector function at all`() {
        assertNull(ViewModelInjector.None.injectorFunction)
    }
}
