plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kspAndroid)
}

android {
    namespace = "kz.evko.navigation.demo.featuresettings"
    compileSdk = 35

    defaultConfig {
        minSdk = 25
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

// 2.0 multi-module demo, module side - a third module, added specifically to verify the
// aggregator mechanism scales past a pair (see demoFeatureLogin's build.gradle.kts for why this
// is raw ksp { arg(...) } rather than the koGenNavigation Gradle plugin).
// shareTabGraph = false - verifies the "not combined by an aggregator" path of @KoGenTab: this
// module builds its own tab locally (tabsHostName default "AppTabsHost") instead of reporting it
// to demoAggregatorApp's manifest - see SettingsScreen.kt.
ksp {
    arg("buildMode", "module")
    arg("moduleName", "demoFeatureSettings")
    arg("packageName", "kz.evko.navigation.demo.featuresettings")
    arg("screenSuffix", "Screen")
    arg("shareTabGraph", "false")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation)

    implementation(project(":koGenNavigation"))
    "ksp"(project(":koGenNavigationCompiler"))
}
