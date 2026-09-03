package org.codirex.publisher.task

import org.codirex.publisher.credentials.CredentialResolver
import org.codirex.publisher.dsl.PublishTarget
import org.codirex.publisher.dsl.PublisherExtension
import org.codirex.publisher.targets.central.BundleGenerator
import org.codirex.publisher.targets.central.CentralUploadWorkAction
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

/**
 * Handles the Maven Central side of `publishAll`:
 *  - `-SNAPSHOT` versions: nothing to do here - maven-publish's own
 *    `publish...ToCentralSnapshotsRepository` task (depended on in
 *    PublisherPlugin) already pushed it directly; this just reports that.
 *  - release versions: bundles the local staging repo (populated by
 *    `publish...ToCentralStagingRepository`) into bundle.zip and uploads it
 *    through the Central Portal API via a Gradle Worker, polling until published.
 *  - `dryRun`: bundles (for releases) but never calls the network client.
 */
open class PublishToCentralTask @Inject constructor(
    private val workerExecutor: WorkerExecutor
) : DefaultTask() {

    @get:Internal
    lateinit var publisherExtension: PublisherExtension

    @get:Internal
    lateinit var stagingDir: File

    init {
        group = "publishing"
        description = "Bundles and uploads this module's release artifacts to Maven Central via the Central Portal."
    }

    @TaskAction
    fun publish() {
        val ext = publisherExtension
        if (PublishTarget.MAVEN_CENTRAL !in ext.targets.enabled) {
            logger.lifecycle("Maven Central not enabled for ${project.path}, skipping.")
            return
        }

        if (ext.isSnapshot) {
            logger.lifecycle(
                "Snapshot ${ext.artifactId}:${ext.version} was pushed directly to Central's snapshot " +
                    "repository - no bundle/validation step needed for snapshots."
            )
            return
        }

        val bundle = BundleGenerator(project).createBundle(stagingDir)
        if (ext.dryRun) {
            logger.lifecycle("[dry-run] Built ${bundle.absolutePath}; skipping Central Portal upload.")
            return
        }

        val credentials = CredentialResolver(project).requireCentral()
        val workQueue = workerExecutor.noIsolation()
        workQueue.submit(CentralUploadWorkAction::class.java) { params ->
            params.bundlePath.set(bundle)
            params.username.set(credentials.username)
            params.password.set(credentials.password)
            params.dryRun.set(false)
        }
        workQueue.await()
    }
}
