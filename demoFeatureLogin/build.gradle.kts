plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kspAndroid)
}

android {
    namespace = "kz.evko.navigation.demo.featurelogin"
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

// 2.0 multi-module demo, module side - see demoAggregatorApp for the combining side.
// Deliberately raw ksp { arg(...) } here, not the koGenNavigation Gradle plugin from
// koGenNavigationGradlePlugin - that plugin isn't published anywhere this repo's own build can
// resolve it from without a prior `publishToMavenLocal`, and this demo needs to build for anyone
// who just clones the repo.
ksp {
    arg("buildMode", "module")
    arg("moduleName", "demoFeatureLogin")
    arg("packageName", "kz.evko.navigation.demo.featurelogin")
    arg("screenSuffix", "Screen")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation)

    implementation(project(":koGenNavigation"))
    "ksp"(project(":koGenNavigationCompiler"))
}
