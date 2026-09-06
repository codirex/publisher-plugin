package org.codirex.publisher.task

import org.codirex.publisher.dsl.PublishTarget
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Prints a readiness report: identity, targets, credentials/signing, and
 * (new in 1.2.0) POM completeness for Maven Central.
 *
 * All state here is plain `Property`/`SetProperty` - no `Project`, no
 * [org.codirex.publisher.dsl.PublisherExtension] held directly. That's not
 * a style preference: a Task field holding the extension transitively holds
 * `Project` (the extension's own constructor parameter), which Configuration
 * Cache cannot serialize - that was a real bug in 1.1.0's version of this
 * class. Everything needed is resolved once, at configuration time, in
 * `PublisherPlugin.configureModule`, and wired in as a `Property`.
 */
abstract class CheckPublisherConfigTask : DefaultTask() {

    @get:Input abstract val groupId: Property<String>
    @get:Input abstract val artifactId: Property<String>
    @get:Input abstract val version: Property<String>
    @get:Input abstract val dryRun: Property<Boolean>
    @get:Input abstract val gpgRequired: Property<Boolean>
    @get:Input abstract val enabledTargets: SetProperty<PublishTarget>

    @get:Input abstract val hasGithubCredentials: Property<Boolean>
    @get:Input abstract val hasCentralCredentials: Property<Boolean>
    @get:Input abstract val hasSigningKey: Property<Boolean>

    @get:Input abstract val hasDescriptionSource: Property<Boolean>
    @get:Input abstract val hasUrlSource: Property<Boolean>
    @get:Input abstract val hasDevelopers: Property<Boolean>

    init {
        group = "publishing"
        description = "Validates that this module is ready to publish (identity, credentials, signing, POM completeness)."
    }

    @TaskAction
    fun check() {
        val problems = mutableListOf<String>()
        val targets = enabledTargets.get()
        val isDryRun = dryRun.get()
        val isSnapshot = version.get().endsWith("-SNAPSHOT")

        if (groupId.get().isBlank() || artifactId.get().isBlank()) problems += "No identity() configured."
        if (targets.isEmpty()) problems += "No targets { } configured."

        // Network credentials aren't required for a dry run - that's the point of it.
        if (!isDryRun) {
            if (PublishTarget.GITHUB_PACKAGES in targets && !hasGithubCredentials.get()) {
                problems += "GitHub Packages selected but no credentials found (GITHUB_ACTOR/GITHUB_TOKEN)."
            }
            if (PublishTarget.MAVEN_CENTRAL in targets && !hasCentralCredentials.get()) {
                problems += "Maven Central selected but no credentials found (CENTRAL_USERNAME/CENTRAL_PASSWORD)."
            }
        }
        // Signing is still validated even for a dry run - verifying it is the point of a dry run.
        if (gpgRequired.get() && !hasSigningKey.get()) {
            problems += "signing.requireGpg() set but no signing key found (SIGNING_KEY/signingKey)."
        }

        // Central rejects bundles with a blank description/url/no developers - catch that
        // before a real upload attempt, not after Central's own validation fails it.
        if (PublishTarget.MAVEN_CENTRAL in targets) {
            if (!hasDescriptionSource.get()) {
                problems += "Maven Central requires a non-blank POM description - set metadata { description = \"...\" } or syncFrom(github(...))."
            }
            if (!hasUrlSource.get()) {
                problems += "Maven Central requires a non-blank POM url - set metadata { projectUrl = \"...\" } or syncFrom(github(...))."
            }
            if (!hasDevelopers.get()) {
                problems += "Maven Central requires at least one <developer> - add metadata { developer(\"id\", \"Name\") }."
            }
        }

        val mode = buildList {
            if (isDryRun) add("dry-run")
            if (isSnapshot) add("snapshot")
        }.let { if (it.isEmpty()) "" else " [${it.joinToString()}]" }

        logger.lifecycle("Publisher config for $path: ${groupId.get()}:${artifactId.get()}:${version.get()}$mode")
        if (problems.isEmpty()) {
            logger.lifecycle("  Ready to publish to: ${targets.joinToString()}")
        } else {
            problems.forEach { logger.error("  x $it") }
            throw GradleException("Publisher config is incomplete for $path; see above.")
        }
    }
}
