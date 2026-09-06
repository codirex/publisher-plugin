plugins {
    `java-gradle-plugin`
    id("org.jetbrains.kotlin.jvm")
    signing
    `maven-publish`
}

group = "org.codirex.publisher"
version = "1.2.1"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17

    withSourcesJar()
    withJavadocJar()
}

kotlin {
    compilerOptions {
        jvmTarget.set(
            org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        )
    }
}

dependencies {
    compileOnly("com.android.tools.build:gradle:8.11.0")

    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation(gradleTestKit())
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

gradlePlugin {
    plugins {
        create("publisherPlugin") {
            id = "org.codirex.publisher"
            implementationClass = "org.codirex.publisher.PublisherPlugin"
            displayName = "Codirex Publisher Plugin"
            description =
                "Zero-config Maven Central / GitHub Packages publishing for the Codirex ecosystem."
        }
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            pom {
                name.set("Codirex Publisher Plugin")
                description.set(
                    "Zero-config Maven Central / GitHub Packages publishing for the Codirex ecosystem."
                )
                url.set("https://github.com/codirex/publisher-plugin")

                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                scm {
                    connection.set(
                        "scm:git:git://github.com/codirex/publisher-plugin.git"
                    )
                    developerConnection.set(
                        "scm:git:ssh://git@github.com/codirex/publisher-plugin.git"
                    )
                    url.set("https://github.com/codirex/publisher-plugin")
                }

                developers {
                    developer {
                        id.set("codirex")
                        name.set("Codirex")
                        url.set("https://github.com/codirex")
                                                email.set("codirex2005@gmail.com")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "BuildRepo"
            url = layout.buildDirectory
                .dir("Build Repo")
                .get()
                .asFile
                .toURI()
        }
    }
}

signing {
    sign(publishing.publications)
}