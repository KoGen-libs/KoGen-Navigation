package kz.evko.navigation.helpers

/**
 * Which DI framework should provide a screen's `ViewModel` parameter - the DSL-facing choice for
 * the `koGenNavigation { viewModelInjector = ... }` Gradle extension.
 *
 * Deliberately lightweight (just a name, no code-generation payload) so it can live here, in the
 * dependency-free common module, instead of pulling the KSP compiler module's KotlinPoet/KSP
 * dependencies onto a Gradle plugin's buildscript classpath just to expose a 3-value choice. The
 * KSP compiler module has its own `ViewModelInjector` enum carrying the actual
 * `MemberName`-typed injector function per framework - matched against this one purely by
 * [diName], the same as it's matched against the raw `viewModelInjector` KSP string option today.
 */
enum class ViewModelInjectorKind(val diName: String) {
    None(""),
    Koin("koin"),
    Hilt("hilt"),
}
