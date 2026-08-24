package kz.evko.navigation.gradle

import kz.evko.navigation.helpers.BuildMode
import kz.evko.navigation.helpers.NavigationAnimation
import kz.evko.navigation.helpers.ViewModelInjectorKind
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

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

    /**
     * Whether this module builds a self-contained app ([BuildMode.Single], the default), a
     * feature module meant to be combined by an aggregator ([BuildMode.Module]), or the module
     * that combines them ([BuildMode.Aggregator]). [Module] and [Aggregator] additionally activate
     * the Gradle-side wiring below - see each property's own doc comment.
     */
    abstract val buildMode: Property<BuildMode>

    /**
     * This module's own name, reported in an aggregator's error messages and used as its
     * manifest's file name, in [BuildMode.Module]. Defaults to the Gradle project's own name
     * (`project.name`) - overriding it is only useful if that's ambiguous or you'd rather it read
     * differently in error messages.
     */
    abstract val moduleName: Property<String>

    /**
     * Which of this module's own build variants' KSP output becomes the manifest published to an
     * aggregator, in [BuildMode.Module] - e.g. `"debug"` reads from the `kspDebugKotlin` task's
     * output. Defaults to `"debug"`. Only meaningful for an Android module with build variants;
     * ignored for a plain Kotlin/JVM one (which has no variants to pick between in the first
     * place).
     *
     * Deliberately variant-*independent* otherwise: a screen's existence/route doesn't usually
     * differ between debug and release, so publishing one variant's manifest for every consumer
     * to read - rather than matching the aggregator's own current variant exactly - is a
     * reasonable simplification, not a real limitation in practice.
     */
    abstract val manifestVariant: Property<String>

    /**
     * The combined `NavHost` function/file's name, in [BuildMode.Aggregator]. Defaults to
     * `"AppNavHost"`.
     */
    abstract val aggregateHostName: Property<String>

    /**
     * The combined `NavHost` function/file's name for this module's own `@KoGenTab`
     * graphs - in [BuildMode.Single], or in [BuildMode.Module] with [shareTabGraph] set to `false`.
     * Defaults to `"AppTabsHost"` - deliberately not `"AppNavHost"` (the default `navHostName`,
     * and [aggregateHostName]'s own default): an untagged group named that already owns that exact
     * file, which this would otherwise collide with.
     */
    abstract val tabsHostName: Property<String>

    /**
     * Whether a [BuildMode.Module] module defers wrapping its own `@KoGenTab` graphs to
     * a [BuildMode.Aggregator] (`true`, the default - lets one tab span more than one module) or
     * builds them itself, locally, as [tabsHostName] - e.g. this module isn't meant to depend on
     * ever being combined by one at all. Ignored outside [BuildMode.Module].
     */
    abstract val shareTabGraph: Property<Boolean>

    /**
     * Every [BuildMode.Module] module to combine, as Gradle project paths (e.g. `":feature-login"`)
     * - required, and only meaningful, in [BuildMode.Aggregator]. Explicit on purpose rather than
     * auto-discovered from this module's whole dependency graph: safer and clearer than guessing
     * which of an arbitrary set of dependencies are actually `@KoGenScreen`-bearing feature
     * modules versus, say, a plain data/networking module that happens to also be a dependency.
     */
    abstract val featureModules: SetProperty<String>
}
