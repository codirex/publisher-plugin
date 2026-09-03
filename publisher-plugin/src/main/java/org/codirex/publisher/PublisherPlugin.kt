package org.codirex.publisher

import org.codirex.publisher.dsl.EcosystemExtension
import org.codirex.publisher.dsl.PublishTarget
import org.codirex.publisher.dsl.PublisherExtension
import org.codirex.publisher.engine.MavenPublishOrchestrator
import org.codirex.publisher.engine.SigningConfigurator
import org.codirex.publisher.task.CheckPublisherConfigTask
import org.codirex.publisher.task.PublishAllTask
import org.codirex.publisher.task.PublishToCentralTask
import org.codirex.publisher.task.PublishToGithubTask
import org.codirex.publisher.task.PublishToMavenLocalTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

/**
 * Entry point for the `org.codirex.publisher` plugin.
 *
 * Applied to a single module, it registers the [PublisherExtension]
 * (`publisher { ... }`) and wires up maven-publish, signing, and the
 * publish-target tasks once that extension is configured.
 *
 * Applied to a multi-module root, it additionally registers the
 * [EcosystemExtension] (`publisherEcosystem { ... }`), which fans shared
 * defaults out to every subproject via `autoPublishModules { }`.
 */
class PublisherPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val publisherExtension = project.extensions.create(
            "publisher",
            PublisherExtension::class.java,
            project
        )

        if (project == project.rootProject) {
            project.extensions.create(
                "publisherEcosystem",
                EcosystemExtension::class.java,
                project
            )
        }

        project.afterEvaluate {
            if (publisherExtension.isConfigured()) {
                configureModule(project, publisherExtension)
            }
        }
    }

    private fun configureModule(project: Project, extension: PublisherExtension) {
        MavenPublishOrchestrator(project, extension).configure()
        SigningConfigurator(project, extension).configure()

        project.tasks.register("checkPublisherConfig", CheckPublisherConfigTask::class.java) { task ->
            task.publisherExtension = extension
        }

        // Matched by repository/target name rather than a hardcoded publication
        // name, so this also works when a component type (e.g. Kotlin
        // Multiplatform) produces more than one MavenPublication.
        val publishToGithub = project.tasks.register(
            "publishToGithubPackages",
            PublishToGithubTask::class.java
        ) { task ->
            task.publisherExtension = extension
            if (PublishTarget.GITHUB_PACKAGES in extension.targets.enabled && !extension.dryRun) {
                task.dependsOn(
                    project.tasks.withType(PublishToMavenRepository::class.java)
                        .matching { it.repository?.name == "GithubPackages" }
                )
            }
        }

        val publishToCentral = project.tasks.register(
            "publishToMavenCentral",
            PublishToCentralTask::class.java
        ) { task ->
            task.publisherExtension = extension
            task.stagingDir = project.layout.buildDirectory.dir("publisher/central-staging").get().asFile
            if (PublishTarget.MAVEN_CENTRAL in extension.targets.enabled) {
                val repoName = if (extension.isSnapshot) "CentralSnapshots" else "CentralStaging"
                // Even in dryRun, the release path still needs the local
                // staging write to happen so BundleGenerator has something
                // to zip; only the snapshot path is a real network push, so
                // that one is skipped entirely for a dry run.
                if (!(extension.isSnapshot && extension.dryRun)) {
                    task.dependsOn(
                        project.tasks.withType(PublishToMavenRepository::class.java)
                            .matching { it.repository?.name == repoName }
                    )
                }
            }
        }

        val publishToMavenLocal = project.tasks.register(
            "publishToLocalMaven",
            PublishToMavenLocalTask::class.java
        ) { task ->
            task.publisherExtension = extension
            if (PublishTarget.MAVEN_LOCAL in extension.targets.enabled) {
                task.dependsOn(project.tasks.withType(PublishToMavenLocal::class.java))
            }
        }

        project.tasks.register("publishAll", PublishAllTask::class.java) { task ->
            task.dependsOn(publishToGithub, publishToCentral, publishToMavenLocal)
        }
    }
}
