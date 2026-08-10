package kz.evko.navigation.gradle

import com.google.devtools.ksp.gradle.KspExtension
import kz.evko.navigation.helpers.NavigationAnimation
import kz.evko.navigation.helpers.ViewModelInjectorKind
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.Properties

private const val KSP_PLUGIN_ID = "com.google.devtools.ksp"

/**
 * Registers the `koGenNavigation { }` DSL (see [KoGenNavigationExtension]), forwards its typed
 * properties into KSP's own `ksp { arg(...) }` options, and adds this library's own
 * runtime/compiler dependencies at the matching version.
 *
 * Deliberately does *not* apply [KSP_PLUGIN_ID] itself. KSP's version is tied tightly to the
 * consuming project's own Kotlin version; bundling one here would risk a silent version mismatch
 * surfacing as a confusing crash inside KSP's own code, rather than a clear error from us. The
 * consumer applies KSP themselves - as most Android/Kotlin projects already do for other reasons
 * - and this plugin just requires it to already be present, erroring plainly if it isn't.
 */
class KoGenNavigationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("koGenNavigation", KoGenNavigationExtension::class.java)

        // Dependencies are added as soon as KSP is applied (via withPlugin), not deferred to
        // afterEvaluate - deferring it that late was a real bug found while verifying this
        // end-to-end: on Android, KSP decides per-variant (kspDebug/kspRelease/...) whether
        // there's anything to process quite early, and a "ksp" dependency added only in
        // afterEvaluate arrived too late for it to notice - the flat "ksp" configuration ended up
        // with our compiler dependency, but "kspDebug" stayed empty and no kspDebugKotlin task
        // was ever created at all.
        project.pluginManager.withPlugin(KSP_PLUGIN_ID) {
            val version = pluginVersion()
            project.dependencies.add("implementation", "io.github.eugenprog:navigation-compose:$version")
            project.dependencies.add("ksp", "io.github.eugenprog:navigation-compose-compiler:$version")
        }

        // The ksp.arg(...) forwarding itself, unlike the dependency-adding above, is *not*
        // timing-sensitive the same way - it only populates a lazily-read option map, so it's
        // safe (and necessary) to do from afterEvaluate, once the user's own koGenNavigation { }
        // configuration block has definitely already run. That matters because packageName/
        // screenSuffix being genuinely *unset* (as opposed to set to "") triggers real inference
        // fallbacks on the compiler side - forwarding a Provider with no value at all into
        // KspExtension.arg(k, Provider<String>) blows up the whole options map when KSP finally
        // reads it ("Cannot query the value... because it has no value available"), so those two
        // are only forwarded at all when actually present; defaultAnimation/viewModelInjector
        // have a real "nothing configured" enum entry (None) that's safe to always forward.
        project.afterEvaluate {
            if (!project.plugins.hasPlugin(KSP_PLUGIN_ID)) {
                throw GradleException(
                    "koGenNavigation requires the KSP plugin. Apply '$KSP_PLUGIN_ID' " +
                        "(a version matching your project's Kotlin version) before applying koGenNavigation.",
                )
            }

            val ksp = project.extensions.getByType(KspExtension::class.java)
            if (extension.packageName.isPresent) ksp.arg("packageName", extension.packageName.get())
            if (extension.screenSuffix.isPresent) ksp.arg("screenSuffix", extension.screenSuffix.get())
            ksp.arg("defaultAnimation", extension.defaultAnimation.getOrElse(NavigationAnimation.None).typeName)
            ksp.arg("viewModelInjector", extension.viewModelInjector.getOrElse(ViewModelInjectorKind.None).diName)
        }
    }

    /**
     * This plugin's own published version, read from a resource generated at build time (see
     * `writeVersionProperties` in this module's build.gradle.kts) - used to pull in the matching
     * runtime/compiler versions, since all three are always released together under the same
     * version number.
     */
    private fun pluginVersion(): String {
        val resource = javaClass.classLoader
            .getResourceAsStream("kogen-navigation-plugin-version.properties")
            ?: throw GradleException(
                "koGenNavigation: couldn't find its own version-properties resource - " +
                    "this indicates a broken build of the plugin itself, not a problem with your project.",
            )
        return resource.use { stream ->
            Properties().apply { load(stream) }.getProperty("version")
                ?: throw GradleException("koGenNavigation: version-properties resource has no 'version' entry.")
        }
    }
}
