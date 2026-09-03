package org.codirex.publisher.dsl

import org.codirex.publisher.credentials.CredentialsDsl
import org.gradle.api.Project
import javax.inject.Inject

/**
 * The `publisher { ... }` block. One of these is created per module the
 * plugin is applied to (see [org.codirex.publisher.PublisherPlugin]).
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

    val metadata: MetadataDsl = MetadataDsl(project)
    val components: ComponentsDsl = ComponentsDsl()
    val credentials: CredentialsDsl = CredentialsDsl()
    val signing: SigningDsl = SigningDsl()
    val targets: TargetsDsl = TargetsDsl()

    fun identity(groupId: String, artifactId: String, version: String) {
        identityRef = IdentityDsl(groupId, artifactId, version)
    }

    fun metadata(action: MetadataDsl.() -> Unit) = metadata.apply(action)

    fun targets(action: TargetsDsl.() -> Unit) = targets.apply(action)

    /** Whether enough has been configured to bother wiring up maven-publish/signing/tasks at all. */
    fun isConfigured(): Boolean = identityRef != null
}
