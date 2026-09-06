package org.codirex.publisher.dsl

import org.codirex.publisher.credentials.CredentialsDsl
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import javax.inject.Inject

/**
 * The `publisher { ... }` block. One of these is created per module the
 * plugin is applied to (see [org.codirex.publisher.PublisherPlugin]).
 *
 * Only ever referenced from configuration-time code (DSL evaluation,
 * [org.codirex.publisher.engine.MavenPublishOrchestrator], etc.) - never
 * held by a `Task` field. Extensions are allowed to hold `Project`; Tasks
 * are not, under Configuration Cache.
 */
open class PublisherExtension @Inject constructor(private val project: Project) {

    internal var identityRef: IdentityDsl? = null
        private set

    val groupId: String get() = identityRef?.groupId.orEmpty()
    val artifactId: String get() = identityRef?.artifactId.orEmpty()
    val version: String get() = identityRef?.version.orEmpty()
    val isSnapshot: Boolean get() = version.endsWith("-SNAPSHOT")

    /**
     * `publisher { dryRun = true }` - generates the POM, runs signing, and
     * builds bundle.zip (for Central) exactly as normal, but skips the
     * actual network upload to GitHub Packages / the Central Portal so you
     * can sanity-check everything first.
     */
    var dryRun: Boolean = false

    val metadata: MetadataDsl = MetadataDsl()
    val components: ComponentsDsl = ComponentsDsl()
    val credentials: CredentialsDsl = CredentialsDsl()
    val signing: SigningDsl = SigningDsl()
    val targets: TargetsDsl = TargetsDsl()

    internal val publicationCustomizers = mutableListOf<MavenPublication.() -> Unit>()

    fun identity(groupId: String, artifactId: String, version: String) {
        require(groupId.isNotBlank()) { "identity(): groupId must not be blank." }
        require(artifactId.isNotBlank()) { "identity(): artifactId must not be blank." }
        require(version.isNotBlank()) { "identity(): version must not be blank." }
        identityRef = IdentityDsl(groupId, artifactId, version)
    }

    /**
     * Shorthand for `identity(project.group, project.name, project.version)`,
     * for modules that already set those on the Gradle project itself.
     */
    fun identity() {
        val g = project.group.toString()
        val v = project.version.toString()
        if (g.isBlank() || g == "unspecified" || v.isBlank() || v == "unspecified") {
            throw GradleException(
                "identity() with no arguments requires project.group and project.version to already be set " +
                    "for '${project.path}' - found group='$g', version='$v'. Either set those, or call " +
                    "identity(groupId, artifactId, version) explicitly."
            )
        }
        identity(g, project.name, v)
    }

    fun metadata(action: MetadataDsl.() -> Unit) = metadata.apply(action)

    fun targets(action: TargetsDsl.() -> Unit) = targets.apply(action)

    /**
     * Escape hatch for anything this plugin doesn't model directly - e.g.
     * attaching a Dokka-generated javadoc jar, or adding an extra artifact.
     * Runs after the plugin's own publication setup (identity, POM,
     * component), once per publication (all of them, for Kotlin
     * Multiplatform's multi-publication case).
     */
    fun publication(action: MavenPublication.() -> Unit) {
        publicationCustomizers += action
    }

    /** Whether enough has been configured to bother wiring up maven-publish/signing/tasks at all. */
    fun isConfigured(): Boolean = identityRef != null
}
