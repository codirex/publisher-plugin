package org.codirex.publisher.metadata

import org.codirex.publisher.dsl.MetadataDsl
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.publish.maven.MavenPom

/**
 * Fills a [MavenPom] from a module's [MetadataDsl], preferring values synced
 * from GitHub and falling back to whatever the module set explicitly.
 *
 * `description`/`url`/`scm.url` are wired through [ProviderFactory.provider]
 * rather than resolved eagerly. `MavenPom`'s properties are themselves lazy
 * `Property<String>`, and the value is only actually computed when Gradle's
 * `GenerateMavenPom` task runs `.get()` on them - meaning `metadata.resolve()`
 * (and the GitHub network call it can trigger) fires when a publish task
 * actually executes, not on every `./gradlew` invocation that merely
 * configures this project. Whether to *emit* the `<scm>`/`<developers>`
 * blocks at all is still decided eagerly (that's static DSL state, no
 * network needed to know it), but their *content* is lazy.
 */
class PomGenerator(
    private val providers: ProviderFactory,
    private val metadata: MetadataDsl,
    private val artifactId: String
) {

    fun apply(pom: MavenPom) {
        pom.name.set(artifactId)
        pom.description.set(providers.provider {
            metadata.description.ifBlank { metadata.resolve()?.description.orEmpty() }
        })
        pom.url.set(providers.provider {
            metadata.projectUrl.ifBlank { metadata.resolve()?.htmlUrl.orEmpty() }
        })

        pom.licenses { licenses ->
            licenses.license { license ->
                license.name.set(metadata.license.name)
                license.url.set(metadata.license.url)
            }
        }

        // Whether to emit <scm> at all is static (no network needed to know
        // it); the actual URL inside it is still lazily resolved.
        if (metadata.hasScmSource()) {
            pom.scm { scm ->
                val scmUrlProvider = providers.provider {
                    metadata.scmUrl.ifBlank { metadata.resolve()?.cloneUrl.orEmpty() }
                }
                scm.url.set(scmUrlProvider)
                scm.connection.set(scmUrlProvider.map { "scm:git:$it" })
                scm.developerConnection.set(scmUrlProvider.map { "scm:git:$it" })
            }
        }

        if (metadata.developers.isNotEmpty()) {
            pom.developers { developers ->
                metadata.developers.forEach { dev ->
                    developers.developer { developer ->
                        developer.id.set(dev.id)
                        developer.name.set(dev.name)
                        dev.email?.let { developer.email.set(it) }
                        dev.url?.let { developer.url.set(it) }
                        if (dev.roles.isNotEmpty()) developer.roles.set(dev.roles)
                    }
                }
            }
        }
    }
}
