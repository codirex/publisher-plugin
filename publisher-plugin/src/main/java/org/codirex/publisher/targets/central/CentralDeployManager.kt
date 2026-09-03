package org.codirex.publisher.targets.central

import kotlinx.coroutines.delay
import org.gradle.api.GradleException
import org.gradle.api.logging.Logging
import java.io.File

/**
 * Orchestrates bundle upload + status polling against the Central Portal.
 * Depends on the [CentralClient] interface (not the concrete
 * [CentralPortalClient]) so it can be unit-tested with a fake client.
 *
 * Note on "non-blocking": Gradle's `Task.doLast`/`WorkAction.execute()` are
 * synchronous by contract, so something in the call chain ultimately has to
 * block until this finishes. What coroutines buy us here is a poll loop that
 * suspends on `delay()` instead of parking a thread on `Thread.sleep()`, and
 * clean composition with [org.codirex.publisher.util.retryWithBackoff]. It's
 * invoked from a Gradle Worker (see [CentralUploadWorkAction]) so at least
 * it's not the main configuration/task-graph thread that pays for the wait.
 */
class CentralDeployManager(private val client: CentralClient) {

    private val logger = Logging.getLogger(CentralDeployManager::class.java)

    suspend fun deploy(
        bundle: File,
        dryRun: Boolean = false,
        pollIntervalMs: Long = 5000,
        maxAttempts: Int = 60
    ) {
        if (dryRun) {
            logger.lifecycle("[dry-run] Would upload ${bundle.absolutePath} to the Central Portal (skipped).")
            return
        }

        val deploymentId = client.uploadBundle(bundle)
        logger.lifecycle("Uploaded to Central Portal, deployment id: $deploymentId")

        repeat(maxAttempts) { attempt ->
            when (val state = client.deploymentStatus(deploymentId)) {
                DeploymentState.PUBLISHED -> {
                    logger.lifecycle("Central Portal deployment $deploymentId published.")
                    return
                }
                DeploymentState.FAILED -> throw GradleException(
                    "Central Portal deployment $deploymentId failed. Check " +
                        "https://central.sonatype.com/publishing/deployments"
                )
                else -> {
                    logger.lifecycle(
                        "Central Portal deployment $deploymentId: $state " +
                            "(attempt ${attempt + 1}/$maxAttempts)"
                    )
                    delay(pollIntervalMs)
                }
            }
        }
        logger.warn(
            "Timed out waiting for deployment $deploymentId to finish. It may still complete; check " +
                "https://central.sonatype.com/publishing/deployments"
        )
    }
}
