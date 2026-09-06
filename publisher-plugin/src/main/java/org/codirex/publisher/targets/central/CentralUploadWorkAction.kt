package org.codirex.publisher.targets.central

import kotlinx.coroutines.runBlocking
import org.codirex.publisher.credentials.CentralCredentials
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

interface CentralUploadParams : WorkParameters {
    /** The local staging repo populated by `publish...ToCentralStagingRepository`. */
    val stagingDir: DirectoryProperty

    /** Where to write bundle.zip before uploading it. */
    val bundleFile: RegularFileProperty

    val username: Property<String>
    val password: Property<String>
    val dryRun: Property<Boolean>
    val autoPublish: Property<Boolean>
}

/**
 * Runs bundling + the actual Central Portal upload+poll inside a Gradle
 * Worker rather than directly on
 * [org.codirex.publisher.task.PublishToCentralTask]'s action thread - see
 * [CentralDeployManager] for what that buys (and doesn't buy) given
 * Gradle's synchronous task-execution model.
 *
 * Deliberately takes only `Property`/`DirectoryProperty`/`RegularFileProperty`
 * parameters (no `Project`, no [org.codirex.publisher.dsl.PublisherExtension])
 * - `WorkParameters` are exactly the boundary Configuration Cache expects
 * execution-time state to cross through.
 */
abstract class CentralUploadWorkAction : WorkAction<CentralUploadParams> {

    private val logger = Logging.getLogger(CentralUploadWorkAction::class.java)

    override fun execute() {
        val bundle = BundleGenerator.createBundle(
            stagingRepoDir = parameters.stagingDir.get().asFile,
            bundleFile = parameters.bundleFile.get().asFile
        )

        if (parameters.dryRun.getOrElse(false)) {
            logger.lifecycle("[dry-run] Built ${bundle.absolutePath}; skipping Central Portal upload.")
            return
        }

        val credentials = CentralCredentials(parameters.username.get(), parameters.password.get())
        val manager = CentralDeployManager(CentralPortalClient(credentials))
        runBlocking {
            manager.deploy(
                bundle = bundle,
                dryRun = false,
                publishingType = if (parameters.autoPublish.getOrElse(true)) "AUTOMATIC" else "USER_MANAGED"
            )
        }
    }
}
