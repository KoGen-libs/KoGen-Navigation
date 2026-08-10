plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kspAndroid)
}

android {
    namespace = "kz.evko.navigation.demo.aggregator"
    compileSdk = 35

    defaultConfig {
        applicationId = "kz.evko.navigation.demo.aggregator"
        minSdk = 25
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    sourceSets.configureEach {
        kotlin.srcDir("$projectDir/build/generated/ksp/$name/kotlin")
    }
}

// 2.0 multi-module demo, aggregator side. Same "raw ksp{}, not the Gradle plugin" reasoning as
// demoFeatureLogin/demoFeatureCart - plus, since this is a single, self-contained build (not
// separately published modules resolved through a real dependency graph), the manifest hand-off
// is a plain Sync task reading each feature module's own generated resources directly, rather
// than the Category-attribute Configuration pair koGenNavigationGradlePlugin registers for real
// (separately published) consumers - that mechanism has its own dedicated test coverage in
// koGenNavigationGradlePlugin itself.
val manifestsDir = layout.buildDirectory.dir("kogenNavigationDemoManifests")
val collectDemoManifests = tasks.register<Sync>("collectDemoManifests") {
    dependsOn(
        ":demoFeatureLogin:kspDebugKotlin",
        ":demoFeatureCart:kspDebugKotlin",
        ":demoFeatureSettings:kspDebugKotlin",
    )
    from(project(":demoFeatureLogin").layout.buildDirectory.dir("generated/ksp/debug/resources"))
    from(project(":demoFeatureCart").layout.buildDirectory.dir("generated/ksp/debug/resources"))
    from(project(":demoFeatureSettings").layout.buildDirectory.dir("generated/ksp/debug/resources"))
    into(manifestsDir)
}

tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }
    .configureEach { dependsOn(collectDemoManifests) }

ksp {
    arg("buildMode", "aggregator")
    arg("packageName", "kz.evko.navigation.demo.aggregator")
    arg("aggregateManifestsDir", manifestsDir.get().asFile.absolutePath)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation)

    implementation(project(":koGenNavigation"))
    "ksp"(project(":koGenNavigationCompiler"))

    implementation(project(":demoFeatureLogin"))
    implementation(project(":demoFeatureCart"))
    implementation(project(":demoFeatureSettings"))
}
