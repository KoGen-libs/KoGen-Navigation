package kz.evko.navigation.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * [ViewModelInjector.diName] is read straight from the `viewModelInjector` KSP option
 * (see ScreenGeneratorProcessor), and getInjectorName/getInjectorImport are spliced verbatim
 * into the generated NavHost. Any change here changes generated code for every consumer.
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
        assertEquals(" = koinViewModel<MyViewModel>(),", ViewModelInjector.Koin.getInjectorName("MyViewModel"))
        assertEquals("import org.koin.androidx.compose.koinViewModel", ViewModelInjector.Koin.getInjectorImport())
    }

    @Test
    fun `Hilt injects via viewModel with no extra import`() {
        assertEquals(" = viewModel<MyViewModel>(),", ViewModelInjector.Hilt.getInjectorName("MyViewModel"))
        assertEquals("", ViewModelInjector.Hilt.getInjectorImport())
    }

    @Test
    fun `None emits neither an assignment nor an import`() {
        assertEquals("", ViewModelInjector.None.getInjectorName("MyViewModel"))
        assertEquals("", ViewModelInjector.None.getInjectorImport())
    }
}
