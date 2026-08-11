package kz.evko.navigation.gradle

import kz.evko.navigation.helpers.NavigationAnimation
import kz.evko.navigation.helpers.ViewModelInjectorKind
import org.gradle.api.provider.Property

/**
 * `koGenNavigation { }` - typed configuration for the KoGen Navigation KSP compiler, registered
 * by [KoGenNavigationPlugin]. Every property is optional; leaving one unset keeps the compiler's
 * own default for it. Forwarded 1:1 into the equivalent `ksp { arg(...) }` string options - this
 * extension only exists to make that configuration typed and autocompletable, not to change what
 * the compiler itself does.
 */
abstract class KoGenNavigationExtension {
    /**
     * Package generated files are written under. Defaults to inferring one from the first
     * annotated screen's own package, plus `.navigation`, if left unset.
     */
    abstract val packageName: Property<String>

    /**
     * Suffix stripped from a screen function's name to derive its route/enum-entry/action name
     * (e.g. `"Screen"` turns `HomeScreen` into `Home`). Nothing is stripped if left unset.
     */
    abstract val screenSuffix: Property<String>

    /**
     * Enter/exit transition applied to screens that don't set their own
     * `@KoGenScreen(animation = ...)`. Defaults to [NavigationAnimation.None] if left unset.
     */
    abstract val defaultAnimation: Property<NavigationAnimation>

    /**
     * Which DI framework provides a screen's `ViewModel` parameter. Defaults to
     * [ViewModelInjectorKind.None] (no injector call generated) if left unset.
     */
    abstract val viewModelInjector: Property<ViewModelInjectorKind>
}
