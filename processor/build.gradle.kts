plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.kspAndroid)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

sourceSets.main {
    java.srcDirs("src/main/kotlin")
}

dependencies{
    implementation(project(":annotation"))
    implementation(libs.symbol.processing.api.v18211011)
}