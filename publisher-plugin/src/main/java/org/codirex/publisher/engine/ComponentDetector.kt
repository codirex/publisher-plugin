package org.codirex.publisher.engine

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension

enum class ComponentType { ANDROID_LIBRARY, JAVA_LIBRARY, KOTLIN_MULTIPLATFORM, UNKNOWN }

/**
 * Figures out what kind of module this is and makes sure its publishable
 * component(s) - with sources + javadoc jars where that's our job to add -
 * are registered.
 *
 * [detect] is only safe to call once every plugin from the `plugins { }`
 * block has already been applied - true inside `afterEvaluate`, which is
 * where [org.codirex.publisher.engine.MavenPublishOrchestrator] calls it.
 *
 * Component *preparation* (`prepareAndroidLibrary`/`prepareJavaLibrary`) is
 * different: it needs to run as early as possible, ideally the moment AGP or
 * java-library actually applies - not deferred to our own `afterEvaluate`.
 * AGP's variant/component wiring happens inside *its own* `afterEvaluate`
 * callbacks; racing that from a plugin-defined `afterEvaluate` registered
 * later is exactly the kind of ordering hazard that produces "component
 * 'release' not found" failures depending on plugin application order.
 * [registerEagerPreparation] sidesteps that by hooking `PluginContainer.withId`
 * at `apply()` time (see [org.codirex.publisher.PublisherPlugin]), which
 * fires immediately if the target plugin is already applied, or later the
 * moment it is - either way, before anything depends on it being ready.
 */
class ComponentDetector(private val project: Project) {

    companion object {
        /**
         * Gradle property (`gradle.properties` / `-P`), not a DSL option:
         * Android component preparation fires eagerly at `apply()` time (see
         * class kdoc above), well before the `publisher { }` block in the
         * build script body has even run - so a DSL flag can't be read yet
         * at the point this decision needs to be made.
         *
         * Set `org.codirex.publisher.skipAndroidJavadoc=true` if you hit
         * AGP's own Dokka-based javadoc generation crashing - a known
         * AGP+Dokka/ASM incompatibility with some modern bytecode (e.g.
         * `PermittedSubclasses requires ASM9` against a Kotlin sealed
         * class), unrelated to this plugin's or your own module's Kotlin
         * version. Sources jar is unaffected either way; when this is set,
         * [org.codirex.publisher.engine.MavenPublishOrchestrator] attaches a
         * plain empty javadoc jar instead of AGP's, since Central still
         * expects one to be *present* even though it won't have real content.
         */
        const val SKIP_ANDROID_JAVADOC_PROPERTY = "org.codirex.publisher.skipAndroidJavadoc"

        fun skipAndroidJavadoc(project: Project): Boolean =
            project.providers.gradleProperty(SKIP_ANDROID_JAVADOC_PROPERTY)
                .map { it.toBoolean() }
                .getOrElse(false)

        fun registerEagerPreparation(project: Project) {
            project.plugins.withId("com.android.library") {
                ComponentDetector(project).prepareAndroidLibrary()
            }
            // "java-library" always applies "java" as part of its own setup,
            // so listening for "java" alone covers both without double-registering.
            project.plugins.withId("java") {
                ComponentDetector(project).prepareJavaLibrary()
            }
            // Kotlin Multiplatform manages its own per-target sources jars;
            // nothing for us to prepare eagerly.
        }
    }

    fun detect(): ComponentType = when {
        project.plugins.hasPlugin("com.android.library") -> ComponentType.ANDROID_LIBRARY
        project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform") -> ComponentType.KOTLIN_MULTIPLATFORM
        project.plugins.hasPlugin("java-library") || project.plugins.hasPlugin("java") ->
            ComponentType.JAVA_LIBRARY
        else -> ComponentType.UNKNOWN
    }

    fun prepareAndroidLibrary() {
        val android = project.extensions.getByType(LibraryExtension::class.java)
        val skipJavadoc = skipAndroidJavadoc(project)
        android.publishing.singleVariant("release") {
            withSourcesJar()
            if (!skipJavadoc) withJavadocJar()
        }
    }

    fun prepareJavaLibrary() {
        project.extensions.getByType(JavaPluginExtension::class.java).apply {
            withSourcesJar()
            withJavadocJar()
        }
    }
}
