plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
    id("signing")
}

group = project.properties["GROUP"].toString()

android {
    namespace = "kz.evko.navigation.runtime"
    compileSdk = 35

    defaultConfig {
        minSdk = 25
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets.getByName("main") {
        java.srcDirs("src/main/kotlin")
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    api(project(":koGenNavigationCommon"))
    api(libs.gson)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.navigation)
    implementation(libs.androidx.ui)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// AGP only registers the "release" software component (needed for `from(components["release"])`
// below) once the library variants have been created, which happens after this script evaluates.
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = properties["GROUP"].toString()
                artifactId = "navigation-compose"

                pom {
                    name.set("KoGen Navigation")
                    description.set("A library for navigation in compose")
                    url.set("https://github.com/KoGen-libs/KoGen-Navigation")

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
}
