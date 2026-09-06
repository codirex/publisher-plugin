package org.codirex.publisher.task

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Lifecycle task for GitHub Packages. The actual upload is done by
 * maven-publish's own generated `publish...ToGithubPackagesRepository`
 * task(s), which this depends on (wired in PublisherPlugin, matched by
 * repository name so it also works for multi-publication setups like
 * Kotlin Multiplatform) whenever the target is enabled and it's not a dry run.
 *
 * Everything here is `@Internal` (not `@Input`): this task has real
 * side effects (or depends on tasks that do) and should never be treated as
 * skippable/up-to-date based on these values - it declares no outputs, so
 * Gradle always runs it regardless, and that's intentional.
 */
abstract class PublishToGithubTask : DefaultTask() {

    @get:Internal abstract val githubEnabled: Property<Boolean>
    @get:Internal abstract val dryRun: Property<Boolean>
    @get:Internal abstract val artifactId: Property<String>

    init {
        group = "publishing"
        description = "Publishes this module's release artifacts to GitHub Packages."
    }

    @TaskAction
    fun report() {
        when {
            !githubEnabled.get() -> logger.lifecycle("GitHub Packages not enabled for $path, skipping.")
            dryRun.get() -> logger.lifecycle("[dry-run] Would publish ${artifactId.get()} to GitHub Packages (upload skipped).")
            else -> logger.lifecycle("Published ${artifactId.get()} to GitHub Packages.")
        }
    }
}
