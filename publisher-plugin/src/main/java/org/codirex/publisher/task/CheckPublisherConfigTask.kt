package org.codirex.publisher.task

import org.codirex.publisher.credentials.CredentialResolver
import org.codirex.publisher.dsl.PublishTarget
import org.codirex.publisher.dsl.PublisherExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/** Prints a readiness report: identity, targets, and which credentials/signing bits are missing. */
open class CheckPublisherConfigTask : DefaultTask() {

    @get:Internal
    lateinit var publisherExtension: PublisherExtension

    init {
        group = "publishing"
        description = "Validates that this module is ready to publish (identity, credentials, signing)."
    }

    @TaskAction
    fun check() {
        val ext = publisherExtension
        val resolver = CredentialResolver(project)
        val problems = mutableListOf<String>()

        if (!ext.isConfigured()) problems += "No identity() configured."
        if (ext.targets.enabled.isEmpty()) problems += "No targets { } configured."

        // Network credentials aren't required for a dry run - that's the point of it.
        if (!ext.dryRun) {
            if (PublishTarget.GITHUB_PACKAGES in ext.targets.enabled && resolver.resolveGithub() == null) {
                problems += "GitHub Packages selected but no credentials found (GITHUB_ACTOR/GITHUB_TOKEN)."
            }
            if (PublishTarget.MAVEN_CENTRAL in ext.targets.enabled && resolver.resolveCentral() == null) {
                problems += "Maven Central selected but no credentials found (CENTRAL_USERNAME/CENTRAL_PASSWORD)."
            }
        }
        // Signing is still validated even for a dry run - that's explicitly part of what dryRun checks.
        if (ext.signing.gpgRequired && resolver.resolveSigningKey() == null) {
            problems += "signing.requireGpg() set but no signing key found (SIGNING_KEY/signingKey)."
        }

        val mode = buildList {
            if (ext.dryRun) add("dry-run")
            if (ext.isSnapshot) add("snapshot")
        }.let { if (it.isEmpty()) "" else " [${it.joinToString()}]" }

        logger.lifecycle("Publisher config for ${project.path}: ${ext.groupId}:${ext.artifactId}:${ext.version}$mode")
        if (problems.isEmpty()) {
            logger.lifecycle("  Ready to publish to: ${ext.targets.enabled.joinToString()}")
        } else {
            problems.forEach { logger.error("  x $it") }
            throw GradleException("Publisher config is incomplete for ${project.path}; see above.")
        }
    }
}
