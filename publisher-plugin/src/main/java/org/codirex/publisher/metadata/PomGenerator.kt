package org.codirex.publisher.metadata

import org.codirex.publisher.dsl.MetadataDsl
import org.gradle.api.publish.maven.MavenPom

/**
 * Fills a [MavenPom] from a module's [MetadataDsl], preferring values synced
 * from GitHub and falling back to whatever the module set explicitly.
 */
class PomGenerator(private val metadata: MetadataDsl, private val artifactId: String) {

    fun apply(pom: MavenPom) {
        val synced = metadata.resolve()

        pom.name.set(artifactId)
        pom.description.set(metadata.description.ifBlank { synced?.description.orEmpty() })
        pom.url.set(metadata.projectUrl.ifBlank { synced?.htmlUrl.orEmpty() })

        pom.licenses { licenses ->
            licenses.license { license ->
                license.name.set(metadata.license.name)
                license.url.set(metadata.license.url)
            }
        }

        val scmUrl = metadata.scmUrl.ifBlank { synced?.cloneUrl.orEmpty() }
        if (scmUrl.isNotBlank()) {
            pom.scm { scm ->
                scm.url.set(scmUrl)
                scm.connection.set("scm:git:$scmUrl")
                scm.developerConnection.set("scm:git:$scmUrl")
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
