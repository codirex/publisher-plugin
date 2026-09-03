package org.codirex.publisher.targets.central

import kotlinx.coroutines.runBlocking
import org.codirex.publisher.credentials.CentralCredentials
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

interface CentralUploadParams : WorkParameters {
    val bundlePath: RegularFileProperty
    val username: Property<String>
    val password: Property<String>
    val dryRun: Property<Boolean>
}

/**
 * Runs the actual Central Portal upload+poll inside a Gradle Worker rather
 * than directly on [org.codirex.publisher.task.PublishToCentralTask]'s
 * action thread - see [CentralDeployManager] for what that buys (and
 * doesn't buy) given Gradle's synchronous task-execution model.
 */
abstract class CentralUploadWorkAction : WorkAction<CentralUploadParams> {

    override fun execute() {
        val credentials = CentralCredentials(parameters.username.get(), parameters.password.get())
        val manager = CentralDeployManager(CentralPortalClient(credentials))
        runBlocking {
            manager.deploy(
                bundle = parameters.bundlePath.get().asFile,
                dryRun = parameters.dryRun.getOrElse(false)
            )
        }
    }
}
