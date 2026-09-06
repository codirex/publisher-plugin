package org.codirex.publisher.engine

import org.codirex.publisher.credentials.CredentialResolver
import org.codirex.publisher.dsl.PublisherExtension
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.api.publish.PublishingExtension
import org.gradle.plugins.signing.SigningExtension

/**
 * Applies and configures the `signing` plugin when `signing { requireGpg() }`
 * was set. Configuration-time only, never touched from a Task.
 *
 * Note on secrets and Configuration Cache: `useInMemoryPgpKeys` needs the
 * actual key/passphrase strings *now* (Gradle's signing plugin API isn't
 * lazy here), so unlike credential lookups elsewhere in this plugin, this
 * one can't be deferred behind a `Provider` read at task-execution time.
 * That also means the resolved key material becomes part of whatever
 * Configuration Cache entry this build produces on disk - treat the cache
 * directory (`.gradle/configuration-cache`) with the same care as any other
 * place secrets might land.
 */
class SigningConfigurator(
    private val project: Project,
    private val extension: PublisherExtension
) {
    private val logger = Logging.getLogger(SigningConfigurator::class.java)

    fun configure() {
        if (!extension.signing.gpgRequired) return

        project.pluginManager.apply("signing")
        val resolver = CredentialResolver(project.providers)
        val key = resolver.signingKey().orNull
        val password = resolver.signingPassword().orNull

        val signingExtension = project.extensions.getByType(SigningExtension::class.java)
        if (!key.isNullOrBlank() && !password.isNullOrBlank()) {
            signingExtension.useInMemoryPgpKeys(key, password)
        } else {
            logger.info(
                "No in-memory signing key found (SIGNING_KEY/signingKey) for '${project.path}'; " +
                    "falling back to the signing plugin's own gpg-agent/keyring lookup."
            )
        }

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        signingExtension.sign(publishing.publications)
    }
}
