package org.codirex.publisher.engine

import org.codirex.publisher.credentials.CredentialResolver
import org.codirex.publisher.dsl.PublisherExtension
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.plugins.signing.SigningExtension

/** Applies and configures the `signing` plugin when `signing { requireGpg() }` was set. */
class SigningConfigurator(
    private val project: Project,
    private val extension: PublisherExtension
) {

    fun configure() {
        if (!extension.signing.gpgRequired) return

        project.pluginManager.apply("signing")
        val resolver = CredentialResolver(project)
        val key = resolver.resolveSigningKey()
        val password = resolver.resolveSigningPassword()

        val signingExtension = project.extensions.getByType(SigningExtension::class.java)
        if (key != null && password != null) {
            signingExtension.useInMemoryPgpKeys(key, password)
        }
        // else: fall back to the signing plugin's own gpg-agent/keyring lookup.

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        signingExtension.sign(publishing.publications)
    }
}
