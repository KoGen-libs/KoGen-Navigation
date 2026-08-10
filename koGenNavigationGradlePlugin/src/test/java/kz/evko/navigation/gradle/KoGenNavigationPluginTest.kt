package kz.evko.navigation.gradle

import com.google.devtools.ksp.gradle.KspExtension
import kz.evko.navigation.helpers.BuildMode
import kz.evko.navigation.helpers.NavigationAnimation
import kz.evko.navigation.helpers.ViewModelInjectorKind
import org.gradle.api.GradleException
import org.gradle.api.attributes.Category
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KoGenNavigationPluginTest {

    @Test
    fun `fails clearly when KSP isn't applied`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(KoGenNavigationPlugin::class.java)

        val failure = assertThrows(GradleException::class.java) {
            (project as ProjectInternal).evaluate()
        }
        // ProjectInternal.evaluate() wraps our GradleException in a ProjectConfigurationException
        // whose own .message is a generic "problem configuring root project" - the real message
        // (and the plugin id it should name) is on the cause chain instead.
        val causeChain = generateSequence(failure as Throwable) { it.cause }
        assertTrue(
            causeChain.any { it.message.orEmpty().contains("com.google.devtools.ksp") },
            "expected some cause to name the missing plugin, got: ${causeChain.map { it.message }.toList()}",
        )
    }

    @Test
    fun `forwards typed extension values into the equivalent ksp arg options`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.pluginManager.apply("com.google.devtools.ksp")
        project.pluginManager.apply(KoGenNavigationPlugin::class.java)

        val extension = project.extensions.getByType(KoGenNavigationExtension::class.java)
        extension.packageName.set("com.example.nav")
        extension.screenSuffix.set("Screen")
        extension.defaultAnimation.set(NavigationAnimation.SlideLeft)
        extension.viewModelInjector.set(ViewModelInjectorKind.Koin)

        (project as ProjectInternal).evaluate()

        val ksp = project.extensions.getByType(KspExtension::class.java)
        assertEquals("com.example.nav", ksp.arguments["packageName"])
        assertEquals("Screen", ksp.arguments["screenSuffix"])
        assertEquals(NavigationAnimation.SlideLeft.typeName, ksp.arguments["defaultAnimation"])
        assertEquals(ViewModelInjectorKind.Koin.diName, ksp.arguments["viewModelInjector"])
    }

    @Test
    fun `leaves packageName and screenSuffix unset when not configured, instead of forwarding an empty string`() {
        // Regression test: packageName/screenSuffix left unset must trigger the compiler's own
        // inference fallback, same as if raw ksp { arg(...) } never mentioned them at all - found
        // by an end-to-end check that an earlier version of this plugin broke, by always
        // forwarding a value (crashing on unset properties, or silently changing behavior for
        // ones defaulted to "").
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.pluginManager.apply("com.google.devtools.ksp")
        project.pluginManager.apply(KoGenNavigationPlugin::class.java)

        (project as ProjectInternal).evaluate()

        val ksp = project.extensions.getByType(KspExtension::class.java)
        assertTrue(!ksp.arguments.containsKey("packageName"))
        assertTrue(!ksp.arguments.containsKey("screenSuffix"))
    }

    @Test
    fun `adds matching implementation and ksp dependencies on its own runtime and compiler`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.pluginManager.apply("com.google.devtools.ksp")
        project.pluginManager.apply(KoGenNavigationPlugin::class.java)

        (project as ProjectInternal).evaluate()

        val implementationDeps = project.configurations.getByName("implementation").dependencies
        val kspDeps = project.configurations.getByName("ksp").dependencies
        assertTrue(implementationDeps.any { it.group == "io.github.eugenprog" && it.name == "navigation-compose" })
        assertTrue(kspDeps.any { it.group == "io.github.eugenprog" && it.name == "navigation-compose-compiler" })
    }

    @Test
    fun `module mode publishes a manifest-tagged outgoing configuration and forwards its own args`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.pluginManager.apply("com.google.devtools.ksp")
        project.pluginManager.apply(KoGenNavigationPlugin::class.java)

        val extension = project.extensions.getByType(KoGenNavigationExtension::class.java)
        extension.buildMode.set(BuildMode.Module)

        (project as ProjectInternal).evaluate()

        val ksp = project.extensions.getByType(KspExtension::class.java)
        assertEquals(BuildMode.Module.argName, ksp.arguments["buildMode"])
        assertEquals(project.name, ksp.arguments["moduleName"])

        val manifestElements = project.configurations.getByName("kogenNavigationManifestElements")
        assertTrue(manifestElements.isCanBeConsumed)
        assertTrue(!manifestElements.isCanBeResolved)
        val category = manifestElements.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE)
        assertEquals("kogen-navigation-manifest", category?.name)
    }

    @Test
    fun `aggregator mode fails clearly with no featureModules configured`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.pluginManager.apply("com.google.devtools.ksp")
        project.pluginManager.apply(KoGenNavigationPlugin::class.java)

        val extension = project.extensions.getByType(KoGenNavigationExtension::class.java)
        extension.buildMode.set(BuildMode.Aggregator)

        val failure = assertThrows(GradleException::class.java) {
            (project as ProjectInternal).evaluate()
        }
        val causeChain = generateSequence(failure as Throwable) { it.cause }
        assertTrue(causeChain.any { it.message.orEmpty().contains("featureModules") })
    }

    @Test
    fun `aggregator mode resolves listed feature modules and wires a collect task`() {
        val root = ProjectBuilder.builder().build()
        val feature = ProjectBuilder.builder().withName("feature-login").withParent(root).build()
        feature.pluginManager.apply("org.jetbrains.kotlin.jvm")

        root.pluginManager.apply("org.jetbrains.kotlin.jvm")
        root.pluginManager.apply("com.google.devtools.ksp")
        root.pluginManager.apply(KoGenNavigationPlugin::class.java)

        val extension = root.extensions.getByType(KoGenNavigationExtension::class.java)
        extension.buildMode.set(BuildMode.Aggregator)
        extension.featureModules.set(setOf(":feature-login"))

        (root as ProjectInternal).evaluate()

        val manifestPath = root.configurations.getByName("kogenNavigationManifestPath")
        assertTrue(!manifestPath.isCanBeConsumed)
        assertTrue(manifestPath.isCanBeResolved)
        assertTrue(manifestPath.dependencies.any { it is org.gradle.api.artifacts.ProjectDependency && it.path == ":feature-login" })

        assertTrue(root.tasks.findByName("collectKogenNavigationManifests") != null)
        val kspTask = root.tasks.getByName("kspKotlin")
        // .dependsOn itself holds the raw declarations (here, a TaskProvider, not a resolved
        // Task) - .taskDependencies.getDependencies(...) is what actually resolves the real graph
        // edge, the same way Gradle itself does to decide execution order.
        assertTrue(kspTask.taskDependencies.getDependencies(kspTask).any { it.name == "collectKogenNavigationManifests" })

        val ksp = root.extensions.getByType(KspExtension::class.java)
        assertEquals("AppNavHost", ksp.arguments["aggregateHostName"])
        assertTrue(ksp.arguments["aggregateManifestsDir"]?.endsWith("kogenNavigation/manifests") == true)
    }
}
