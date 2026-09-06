package org.codirex.publisher.task

import org.codirex.publisher.targets.central.CentralUploadWorkAction
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

/**
 * Handles the Maven Central side of `publishAll`:
 *  - `-SNAPSHOT` versions: nothing to do here - maven-publish's own
 *    `publish...ToCentralSnapshotsRepository` task (depended on in
 *    PublisherPlugin) already pushed it directly; this just reports that.
 *  - release versions: submits a [CentralUploadWorkAction] that bundles the
 *    local staging repo (populated by `publish...ToCentralStagingRepository`)
 *    and uploads it through the Central Portal API, polling until published.
 *  - `dryRun`: the worker still builds the bundle (so you can inspect it)
 *    but never calls the network client.
 *
 * `centralUsername`/`centralPassword` are `@Internal`, deliberately not
 * `@Input`: Gradle hashes `@Input` values into the task's up-to-date/build-cache
 * fingerprint, and secrets have no business ending up in a (potentially
 * shared/remote) build cache key. Nothing here declares an `@OutputFile`
 * either - the bundle and the network upload are real side effects, not a
 * reproducible output Gradle should ever consider skipping via up-to-date checks.
 */
abstract class PublishToCentralTask @Inject constructor(
    private val workerExecutor: WorkerExecutor
) : DefaultTask() {

    @get:Internal abstract val centralEnabled: Property<Boolean>
    @get:Internal abstract val isSnapshot: Property<Boolean>
    @get:Internal abstract val dryRun: Property<Boolean>
    @get:Internal abstract val autoPublish: Property<Boolean>
    @get:Internal abstract val artifactId: Property<String>
    @get:Internal abstract val version: Property<String>

    @get:Internal abstract val stagingDir: DirectoryProperty
    @get:Internal abstract val bundleFile: RegularFileProperty

    @get:Internal abstract val centralUsername: Property<String>
    @get:Internal abstract val centralPassword: Property<String>

    init {
        group = "publishing"
        description = "Bundles and uploads this module's release artifacts to Maven Central via the Central Portal."
    }

    @TaskAction
    fun publish() {
        if (!centralEnabled.get()) {
            logger.lifecycle("Maven Central not enabled for $path, skipping.")
            return
        }

        if (isSnapshot.get()) {
            logger.lifecycle(
                "Snapshot ${artifactId.get()}:${version.get()} was pushed directly to Central's snapshot " +
                    "repository - no bundle/validation step needed for snapshots."
            )
            return
        }

        val workQueue = workerExecutor.noIsolation()
        workQueue.submit(CentralUploadWorkAction::class.java) { params ->
            params.stagingDir.set(stagingDir)
            params.bundleFile.set(bundleFile)
            params.username.set(centralUsername.orElse(""))
            params.password.set(centralPassword.orElse(""))
            params.dryRun.set(dryRun)
            params.autoPublish.set(autoPublish)
        }
        workQueue.await()
    }
}
