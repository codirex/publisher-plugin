package org.codirex.publisher.engine

import com.android.build.gradle.LibraryExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension

enum class ComponentType { ANDROID_LIBRARY, JAVA_LIBRARY, KOTLIN_MULTIPLATFORM, UNKNOWN }

/**
 * Figures out what kind of module this is and makes sure its publishable
 * component(s) - with sources + javadoc jars where that's our job to add -
 * are registered before maven-publish looks for them.
 */
class ComponentDetector(private val project: Project) {

    fun detect(): ComponentType = when {
        project.plugins.hasPlugin("com.android.library") -> ComponentType.ANDROID_LIBRARY
        project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform") -> ComponentType.KOTLIN_MULTIPLATFORM
        project.plugins.hasPlugin("java-library") || project.plugins.hasPlugin("java") ->
            ComponentType.JAVA_LIBRARY
        else -> ComponentType.UNKNOWN
    }

    fun prepare(type: ComponentType) {
        when (type) {
            ComponentType.ANDROID_LIBRARY -> prepareAndroidLibrary()
            ComponentType.JAVA_LIBRARY -> prepareJavaLibrary()
            ComponentType.KOTLIN_MULTIPLATFORM -> {
                // The kotlin-multiplatform plugin + maven-publish already
                // auto-create one MavenPublication per target (jvm, js,
                // native...) plus a root "kotlinMultiplatform" metadata
                // publication - we don't create or replace any of those.
                // Per-target sources jars are controlled by that target's
                // own `withSourcesJar()` in the `kotlin { }` block; we don't
                // override that here since it varies per target type.
            }
            ComponentType.UNKNOWN -> throw IllegalStateException(
                "Publisher plugin could not detect a publishable component on '${project.path}'. " +
                    "Apply 'com.android.library', 'java-library', or 'org.jetbrains.kotlin.multiplatform' " +
                    "before 'org.codirex.publisher'."
            )
        }
    }

    private fun prepareAndroidLibrary() {
        val android = project.extensions.getByType(LibraryExtension::class.java)
        android.publishing.singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    private fun prepareJavaLibrary() {
        project.extensions.getByType(JavaPluginExtension::class.java).apply {
            withSourcesJar()
            withJavadocJar()
        }
    }
}
