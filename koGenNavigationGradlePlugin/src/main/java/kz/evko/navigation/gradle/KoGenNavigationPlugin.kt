package kz.evko.navigation.gradle

import com.google.devtools.ksp.gradle.KspExtension
import kz.evko.navigation.helpers.BuildMode
import kz.evko.navigation.helpers.NavigationAnimation
import kz.evko.navigation.helpers.ViewModelInjectorKind
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Category
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import java.util.Properties

private const val KSP_PLUGIN_ID = "com.google.devtools.ksp"

/** The `Category` attribute value tagging the outgoing/resolvable manifest-exchange `Configuration` pair below. */
private const val MANIFEST_CATEGORY = "kogen-navigation-manifest"

/**
 * Registers the `koGenNavigation { }` DSL (see [KoGenNavigationExtension]), forwards its typed
 * properties into KSP's own `ksp { arg(...) }` options, adds this library's own runtime/compiler
 * dependencies at the matching version, and - only when [BuildMode.Module]/[BuildMode.Aggregator]
 * are actually selected - the Gradle-side machinery that hands a `BuildMode.Module` module's
 * manifest across to a `BuildMode.Aggregator` module (see [registerManifestProducer]/
 * [registerManifestConsumer]). [BuildMode.Single] activates none of that - same as not applying
 * this plugin at all for it.
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
        extension.buildMode.convention(BuildMode.Single)
        extension.moduleName.convention(project.name)
        extension.manifestVariant.convention("debug")
        extension.aggregateHostName.convention("AppNavHost")

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

        // The rest - ksp.arg(...) forwarding and the manifest-exchange Configurations - isn't
        // timing-sensitive the same way (it only populates a lazily-read option map, or declares
        // dependencies on a Configuration of our own rather than KSP's), so it's safe (and
        // necessary) to do from afterEvaluate, once the user's own koGenNavigation { } block has
        // definitely already run - buildMode/featureModules/etc. aren't known before that.
        project.afterEvaluate {
            if (!project.plugins.hasPlugin(KSP_PLUGIN_ID)) {
                throw GradleException(
                    "koGenNavigation requires the KSP plugin. Apply '$KSP_PLUGIN_ID' " +
                        "(a version matching your project's Kotlin version) before applying koGenNavigation.",
                )
            }

            val ksp = project.extensions.getByType(KspExtension::class.java)
            // packageName/screenSuffix are only forwarded when actually present -
            // defaultAnimation/viewModelInjector have a real "nothing configured" enum entry
            // (None) that's safe to always forward instead; forwarding an empty string for the
            // former two would silently break the compiler's own inference fallback for them.
            if (extension.packageName.isPresent) ksp.arg("packageName", extension.packageName.get())
            if (extension.screenSuffix.isPresent) ksp.arg("screenSuffix", extension.screenSuffix.get())
            ksp.arg("defaultAnimation", extension.defaultAnimation.getOrElse(NavigationAnimation.None).typeName)
            ksp.arg("viewModelInjector", extension.viewModelInjector.getOrElse(ViewModelInjectorKind.None).diName)

            val buildMode = extension.buildMode.get()
            ksp.arg("buildMode", buildMode.argName)

            when (buildMode) {
                BuildMode.Single -> Unit
                BuildMode.Module -> {
                    ksp.arg("moduleName", extension.moduleName.get())
                    registerManifestProducer(project, extension)
                }
                BuildMode.Aggregator -> {
                    ksp.arg("aggregateHostName", extension.aggregateHostName.get())
                    ksp.arg("aggregateManifestsDir", registerManifestConsumer(project, extension))
                }
            }
        }
    }

    /**
     * The producer half of the manifest exchange, for [BuildMode.Module]: publishes the manifest
     * KSP writes (for [extension]'s chosen [KoGenNavigationExtension.manifestVariant]) as an
     * outgoing `Configuration`, tagged with [MANIFEST_CATEGORY] so [registerManifestConsumer]
     * elsewhere can find *only* this - not this module's normal compiled output - when it
     * resolves its own dependency on this project.
     */
    private fun registerManifestProducer(project: Project, extension: KoGenNavigationExtension) {
        val manifestElements = project.configurations.create("kogenNavigationManifestElements") {
            it.isCanBeConsumed = true
            it.isCanBeResolved = false
            it.attributes.attribute(
                Category.CATEGORY_ATTRIBUTE,
                project.objects.named(Category::class.java, MANIFEST_CATEGORY),
            )
        }

        val variant = extension.manifestVariant.get()
        val variantTaskName = "ksp${variant.replaceFirstChar { char -> char.uppercase() }}Kotlin"
        // Found by actually adding a product flavor to a real Android module and checking: with
        // flavors, there is no plain "kspDebugKotlin" *or* "kspKotlin" at all - the real task is
        // "kspFreeDebugKotlin"/"kspPaidDebugKotlin"/etc. The old fallback (silently trying
        // "kspKotlin" whenever the exact variant name wasn't found) would throw a raw, confusing
        // UnknownTaskException in that case - a flavored module needs manifestVariant set
        // explicitly, and should be told so clearly, with the actual options.
        val kspTaskName = when {
            project.tasks.findByName(variantTaskName) != null -> variantTaskName
            project.tasks.findByName("kspKotlin") != null -> "kspKotlin" // plain Kotlin/JVM module - no variants at all
            else -> {
                val available = project.tasks.names.filter { it.startsWith("ksp") && it.endsWith("Kotlin") }.sorted()
                throw GradleException(
                    "koGenNavigation: buildMode = \"module\" couldn't find a KSP task named " +
                        "\"$variantTaskName\" (nor the plain \"kspKotlin\"). This usually means this " +
                        "module has product flavors, so its real KSP task names differ - set " +
                        "manifestVariant explicitly to one of: " +
                        available.ifEmpty { listOf("<none - is the KSP plugin actually applied here?>") },
                )
            }
        }
        val resourcesVariantDir = if (kspTaskName == variantTaskName) variant else "main"

        val manifestFile = project.layout.buildDirectory.file(
            "generated/ksp/$resourcesVariantDir/resources/META-INF/kogen-navigation/${extension.moduleName.get()}.json",
        )
        project.artifacts.add(manifestElements.name, manifestFile) {
            it.builtBy(project.tasks.named(kspTaskName))
        }
    }

    /**
     * The consumer half, for [BuildMode.Aggregator]: resolves every [KoGenNavigationExtension.featureModules]
     * project's [MANIFEST_CATEGORY]-tagged artifact (leniently - a listed project that, say,
     * doesn't actually apply `buildMode = "module"` is simply skipped, not a hard failure), copies
     * them all into one directory via a real [Sync] task (whose `from(...)` on a `Configuration`-backed
     * `FileCollection` automatically depends on whatever builds those files - no manual wiring
     * needed there), and makes every KSP task in this project depend on that [Sync] finishing
     * first - the fix for the exact ordering bug this design hit and fixed once already, just
     * generalized: reading a directory's contents is only safe once whatever populates it is
     * guaranteed to have already run.
     *
     * @return The directory [Sync] copies every manifest into, for the KSP option to point at.
     */
    private fun registerManifestConsumer(project: Project, extension: KoGenNavigationExtension): Provider<String> {
        val manifestPath = project.configurations.create("kogenNavigationManifestPath") {
            it.isCanBeConsumed = false
            it.isCanBeResolved = true
            it.attributes.attribute(
                Category.CATEGORY_ATTRIBUTE,
                project.objects.named(Category::class.java, MANIFEST_CATEGORY),
            )
        }

        val modules = extension.featureModules.getOrElse(emptySet())
        if (modules.isEmpty()) {
            throw GradleException(
                "koGenNavigation: buildMode = \"aggregator\" requires featureModules to list at least " +
                    "one project path (e.g. featureModules.set(setOf(\":feature-login\"))).",
            )
        }
        modules.forEach { path ->
            project.dependencies.add(manifestPath.name, project.dependencies.project(mapOf("path" to path)))
        }

        val manifestsDir = project.layout.buildDirectory.dir("kogenNavigation/manifests")
        val collectManifests = project.tasks.register("collectKogenNavigationManifests", Sync::class.java) {
            it.from(manifestPath.incoming.artifactView { view -> view.isLenient = true }.files)
            it.into(manifestsDir)
        }

        project.tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }
            .configureEach { it.dependsOn(collectManifests) }

        return manifestsDir.map { dir -> dir.asFile.absolutePath }
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
