plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
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
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])

            groupId = properties["GROUP"].toString()
            artifactId = "navigation-compose-common"

            pom {
                name.set("KoGen Navigation Common")
                description.set("Shared annotation and types used by both the KoGen Navigation runtime and its KSP compiler")
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
