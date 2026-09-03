package org.codirex.publisher.task

import org.codirex.publisher.dsl.PublishTarget
import org.codirex.publisher.dsl.PublisherExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Lifecycle task for GitHub Packages. The actual upload is done by
 * maven-publish's own generated `publish...ToGithubPackagesRepository`
 * task(s), which this depends on (wired in PublisherPlugin, matched by
 * repository name so it also works for multi-publication setups like
 * Kotlin Multiplatform) whenever the target is enabled and it's not a dry run.
 */
open class PublishToGithubTask : DefaultTask() {

    @get:Internal
    lateinit var publisherExtension: PublisherExtension

    init {
        group = "publishing"
        description = "Publishes this module's release artifacts to GitHub Packages."
    }

    @TaskAction
    fun report() {
        val ext = publisherExtension
        when {
            PublishTarget.GITHUB_PACKAGES !in ext.targets.enabled ->
                logger.lifecycle("GitHub Packages not enabled for ${project.path}, skipping.")
            ext.dryRun ->
                logger.lifecycle("[dry-run] Would publish ${ext.artifactId} to GitHub Packages (upload skipped).")
            else ->
                logger.lifecycle("Published ${ext.artifactId} to GitHub Packages.")
        }
    }
}
