package org.codirex.publisher.task

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Lifecycle task for `targets { mavenLocal() }`. maven-publish already
 * creates a `publish<Pub>PublicationToMavenLocal` task per publication
 * regardless of our targets - this just depends on all of them (wired in
 * PublisherPlugin) and reports when the target's actually selected.
 */
abstract class PublishToMavenLocalTask : DefaultTask() {

    @get:Internal abstract val mavenLocalEnabled: Property<Boolean>
    @get:Internal abstract val groupId: Property<String>
    @get:Internal abstract val artifactId: Property<String>
    @get:Internal abstract val version: Property<String>

    init {
        group = "publishing"
        description = "Publishes this module's artifacts to the local Maven repository (~/.m2/repository)."
    }

    @TaskAction
    fun report() {
        if (!mavenLocalEnabled.get()) {
            logger.lifecycle("Maven Local not enabled for $path, skipping.")
        } else {
            logger.lifecycle("Published ${groupId.get()}:${artifactId.get()}:${version.get()} to ~/.m2/repository.")
        }
    }
}
