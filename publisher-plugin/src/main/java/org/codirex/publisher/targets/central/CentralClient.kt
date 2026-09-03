package org.codirex.publisher.targets.central

import java.io.File

enum class DeploymentState { PENDING, VALIDATING, VALIDATED, PUBLISHING, PUBLISHED, FAILED, UNKNOWN }

/**
 * Abstraction over the Sonatype Central Publisher Portal API. Isolating this
 * as an interface (rather than depending on [CentralPortalClient] directly)
 * means [CentralDeployManager] can be unit-tested against a fake/mock, and
 * insulates the rest of the plugin if Sonatype's API shape changes again -
 * only [CentralPortalClient] would need updating.
 */
interface CentralClient {
    suspend fun uploadBundle(bundle: File, publishingType: String = "AUTOMATIC"): String
    suspend fun deploymentStatus(deploymentId: String): DeploymentState
}
