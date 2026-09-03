package org.codirex.publisher.task

import org.codirex.publisher.dsl.PublishTarget
import org.codirex.publisher.dsl.PublisherExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Lifecycle task for `targets { mavenLocal() }`. maven-publish already
 * creates a `publish<Pub>PublicationToMavenLocal` task per publication
 * regardless of our targets - this just depends on all of them (wired in
 * PublisherPlugin) and reports when the target's actually selected.
 */
open class PublishToMavenLocalTask : DefaultTask() {

    @get:Internal
    lateinit var publisherExtension: PublisherExtension

    init {
        group = "publishing"
        description = "Publishes this module's artifacts to the local Maven repository (~/.m2/repository)."
    }

    @TaskAction
    fun report() {
        val ext = publisherExtension
        if (PublishTarget.MAVEN_LOCAL !in ext.targets.enabled) {
            logger.lifecycle("Maven Local not enabled for ${project.path}, skipping.")
        } else {
            logger.lifecycle("Published ${ext.groupId}:${ext.artifactId}:${ext.version} to ~/.m2/repository.")
        }
    }
}
