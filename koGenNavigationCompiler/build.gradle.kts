plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.jreleaser)
    id("maven-publish")
    id("signing")
}

group = project.properties["GROUP"].toString()

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.symbol.processing)
    implementation(project(":koGenNavigationCommon"))
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    // Serializes/deserializes ModuleManifest (buildMode = module/aggregator) - this is the
    // compiler's own internal use, unrelated to the Gson the *generated* code references for
    // untyped route arguments (see ArgumentTypes/RoutesListGenerator).
    implementation(libs.gson)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlin.compile.testing)
    testImplementation(libs.kotlin.compile.testing.ksp)

    constraints {
        implementation("org.apache.commons:commons-compress:1.26.2") {
            because("JReleaser requires this version to avoid a conflict")
        }
    }
}

tasks.test {
    useJUnitPlatform()
    // kotlin-compile-testing spins up an in-process javac/kotlinc; give it room to run.
    maxHeapSize = "2g"
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin") {
    compilerOptions {
        // kotlin-compile-testing's whole API is marked @ExperimentalCompilerApi.
        freeCompilerArgs.add("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])

            groupId = properties["GROUP"].toString()
            artifactId = "navigation-compose-compiler"

            pom {
                name.set("KoGen Navigation Compiler")
                description.set("KSP symbol processor that generates the compose navigation code for KoGen Navigation")
                url.set("https://github.com/EugenProg/KoGen-navigation_demo")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("EugenProg")
                        name.set("Eugen Kopp")
                        email.set("Eugen.kopp.kz@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/EugenProg/KoGen-Navigation.git")
                    developerConnection.set("scm:git:ssh://github.com:EugenProg/KoGen-Navigation.git")
                    url.set("https://github.com/EugenProg/KoGen-Navigation/tree/master")
                }
            }
        }
    }
    repositories {
        maven {
            setUrl(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

val signingKey = System.getenv("JRELEASER_GPG_SECRET_KEY")
val signingPassword = System.getenv("JRELEASER_GPG_PASSPHRASE")
if (!signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
    signing {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["release"])
    }
}
