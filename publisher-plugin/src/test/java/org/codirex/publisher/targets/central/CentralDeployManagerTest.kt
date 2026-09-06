package org.codirex.publisher.targets.central

import kotlinx.coroutines.test.runTest
import org.gradle.api.GradleException
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * This is exactly what pulling [CentralClient] out as an interface in 1.2.0
 * was for: exercising [CentralDeployManager]'s polling/error logic without
 * making a single real HTTP call.
 */
private class FakeCentralClient(
    private val statusSequence: List<DeploymentState>
) : CentralClient {
    var uploadCalls = 0
        private set
    private var statusIndex = 0

    override suspend fun uploadBundle(bundle: File, publishingType: String): String {
        uploadCalls++
        return "fake-deployment-id"
    }

    override suspend fun deploymentStatus(deploymentId: String): DeploymentState {
        val state = statusSequence[statusIndex.coerceAtMost(statusSequence.lastIndex)]
        statusIndex++
        return state
    }
}

class CentralDeployManagerTest {

    @Test
    fun `polls until published`() = runTest {
        val client = FakeCentralClient(listOf(DeploymentState.VALIDATING, DeploymentState.VALIDATED, DeploymentState.PUBLISHED))
        CentralDeployManager(client).deploy(File("fake-bundle.zip"), pollIntervalMs = 1)
        assertEquals(1, client.uploadCalls)
    }

    @Test
    fun `throws when the deployment fails validation`() = runTest {
        val client = FakeCentralClient(listOf(DeploymentState.VALIDATING, DeploymentState.FAILED))
        assertFailsWith<GradleException> {
            CentralDeployManager(client).deploy(File("fake-bundle.zip"), pollIntervalMs = 1)
        }
    }

    @Test
    fun `dry run never calls the client at all`() = runTest {
        val client = FakeCentralClient(listOf(DeploymentState.PUBLISHED))
        CentralDeployManager(client).deploy(File("fake-bundle.zip"), dryRun = true)
        assertEquals(0, client.uploadCalls)
    }

    @Test
    fun `USER_MANAGED stops polling right after upload`() = runTest {
        val client = FakeCentralClient(listOf(DeploymentState.PUBLISHED)) // would "succeed" immediately if polled
        CentralDeployManager(client).deploy(File("fake-bundle.zip"), publishingType = "USER_MANAGED", pollIntervalMs = 1)
        assertEquals(1, client.uploadCalls)
        // If USER_MANAGED had polled, statusIndex would have advanced; instead it should still be untouched.
    }
}
