package org.codirex.publisher

import org.codirex.publisher.credentials.CredentialResolver
import org.codirex.publisher.dsl.EcosystemExtension
import org.codirex.publisher.dsl.PublishTarget
import org.codirex.publisher.dsl.PublisherExtension
import org.codirex.publisher.engine.ComponentDetector
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
        // Eager, plugin-reactive: fires the moment AGP/java-library is
        // actually applied rather than being deferred into our own
        // afterEvaluate below, which would race AGP's own internal
        // afterEvaluate-based variant/component setup depending on
        // registration order. See ComponentDetector's kdoc.
        ComponentDetector.registerEagerPreparation(project)

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

        val resolver = CredentialResolver(project.providers)
        val targets = extension.targets.enabled

        project.tasks.register("checkPublisherConfig", CheckPublisherConfigTask::class.java) { task ->
            task.groupId.set(extension.groupId)
            task.artifactId.set(extension.artifactId)
            task.version.set(extension.version)
            task.dryRun.set(extension.dryRun)
            task.gpgRequired.set(extension.signing.gpgRequired)
            task.enabledTargets.set(targets)
            task.hasGithubCredentials.set(resolver.hasGithub())
            task.hasCentralCredentials.set(resolver.hasCentral())
            task.hasSigningKey.set(resolver.hasSigningKey())
            task.hasDescriptionSource.set(extension.metadata.hasDescriptionSource())
            task.hasUrlSource.set(extension.metadata.hasUrlSource())
            task.hasDevelopers.set(extension.metadata.developers.isNotEmpty())
        }

        // Matched by repository/target name rather than a hardcoded publication
        // name, so this also works when a component type (e.g. Kotlin
        // Multiplatform) produces more than one MavenPublication.
        val publishToGithub = project.tasks.register(
            "publishToGithubPackages",
            PublishToGithubTask::class.java
        ) { task ->
            task.githubEnabled.set(PublishTarget.GITHUB_PACKAGES in targets)
            task.dryRun.set(extension.dryRun)
            task.artifactId.set(extension.artifactId)
            if (PublishTarget.GITHUB_PACKAGES in targets && !extension.dryRun) {
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
            val isSnapshot = extension.isSnapshot
            task.centralEnabled.set(PublishTarget.MAVEN_CENTRAL in targets)
            task.isSnapshot.set(isSnapshot)
            task.dryRun.set(extension.dryRun)
            task.autoPublish.set(extension.targets.centralAutoPublish)
            task.artifactId.set(extension.artifactId)
            task.version.set(extension.version)
            task.stagingDir.set(project.layout.buildDirectory.dir("publisher/central-staging"))
            task.bundleFile.set(project.layout.buildDirectory.file("publisher/bundle.zip"))
            task.centralUsername.set(resolver.centralUsername())
            task.centralPassword.set(resolver.centralPassword())

            if (PublishTarget.MAVEN_CENTRAL in targets && !isSnapshot) {
                // Even in dryRun, the release path still needs the local
                // staging write to happen so the bundler has something to
                // zip; only the snapshot path is a real network push, so
                // that dependency is skipped in dryRun instead.
                task.dependsOn(
                    project.tasks.withType(PublishToMavenRepository::class.java)
                        .matching { it.repository?.name == "CentralStaging" }
                )
            } else if (PublishTarget.MAVEN_CENTRAL in targets && isSnapshot && !extension.dryRun) {
                task.dependsOn(
                    project.tasks.withType(PublishToMavenRepository::class.java)
                        .matching { it.repository?.name == "CentralSnapshots" }
                )
            }
        }

        val publishToMavenLocal = project.tasks.register(
            "publishToLocalMaven",
            PublishToMavenLocalTask::class.java
        ) { task ->
            task.mavenLocalEnabled.set(PublishTarget.MAVEN_LOCAL in targets)
            task.groupId.set(extension.groupId)
            task.artifactId.set(extension.artifactId)
            task.version.set(extension.version)
            if (PublishTarget.MAVEN_LOCAL in targets) {
                task.dependsOn(project.tasks.withType(PublishToMavenLocal::class.java))
            }
        }

        project.tasks.register("publishAll", PublishAllTask::class.java) { task ->
            task.dependsOn(publishToGithub, publishToCentral, publishToMavenLocal)
        }
    }
}
