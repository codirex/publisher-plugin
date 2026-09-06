package org.codirex.publisher.engine

import org.codirex.publisher.credentials.CredentialResolver
import org.codirex.publisher.dsl.PublishTarget
import org.codirex.publisher.dsl.PublisherExtension
import org.codirex.publisher.metadata.PomGenerator
import org.codirex.publisher.targets.github.GithubPackagesClient
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

/**
 * Applies `maven-publish` and wires up a publication for whichever component
 * [ComponentDetector] finds, then registers a repository for each enabled
 * [PublishTarget]:
 *  - GITHUB_PACKAGES -> pushes straight to https://maven.pkg.github.com/<owner>/<repo>
 *  - MAVEN_CENTRAL, release version -> writes to a local staging dir;
 *    [org.codirex.publisher.task.PublishToCentralTask] bundles and uploads
 *    it via the Central Portal API afterwards.
 *  - MAVEN_CENTRAL, -SNAPSHOT version -> pushes straight to Central's
 *    snapshot repository (no bundle/validate/publish dance - snapshots
 *    publish immediately, see https://central.sonatype.org/publish/publish-portal-snapshots/).
 *  - MAVEN_LOCAL -> nothing to register; maven-publish already creates a
 *    `publish<Pub>PublicationToMavenLocal` task for every publication.
 *
 * Only ever used at configuration time (from `PublisherPlugin.configureModule`,
 * inside `afterEvaluate`) - never stored as task state, so it's fine for this
 * class to hold `Project` directly.
 */
class MavenPublishOrchestrator(
    private val project: Project,
    private val extension: PublisherExtension
) {
    companion object {
        const val CENTRAL_SNAPSHOTS_URL = "https://central.sonatype.com/repository/maven-snapshots/"
    }

    private val credentials = CredentialResolver(project.providers)

    fun configure() {
        project.pluginManager.apply("maven-publish")

        val componentType = ComponentDetector(project).detect()
        if (componentType == ComponentType.UNKNOWN) {
            throw GradleException(
                "Publisher plugin could not detect a publishable component on '${project.path}'. " +
                    "Apply 'com.android.library', 'java-library', or 'org.jetbrains.kotlin.multiplatform' " +
                    "before 'org.codirex.publisher'."
            )
        }

        project.extensions.configure(PublishingExtension::class.java) { publishing ->
            when (componentType) {
                ComponentType.KOTLIN_MULTIPLATFORM -> configureKotlinMultiplatform(publishing)
                ComponentType.ANDROID_LIBRARY -> configureSingleComponent(publishing, "release")
                ComponentType.JAVA_LIBRARY -> configureSingleComponent(publishing, "java")
                ComponentType.UNKNOWN -> Unit // handled above
            }

            registerCentralRepository(publishing)
            registerGithubRepository(publishing)
            // MAVEN_LOCAL needs no repository - maven-publish provides publishToMavenLocal for free.
        }
    }

    private fun configureSingleComponent(publishing: PublishingExtension, componentName: String) {
        publishing.publications.create("release", MavenPublication::class.java) { publication ->
            publication.from(project.components.getByName(componentName))
            publication.groupId = extension.groupId
            publication.artifactId = extension.artifactId
            publication.version = extension.version
            PomGenerator(project.providers, extension.metadata, extension.artifactId).apply(publication.pom)
            extension.publicationCustomizers.forEach { customize -> publication.customize() }
        }
    }

    private fun configureKotlinMultiplatform(publishing: PublishingExtension) {
        // kotlin-multiplatform + maven-publish already created one
        // publication per target; just brand every one of them with our
        // identity/POM instead of creating a competing "release" publication.
        publishing.publications.withType(MavenPublication::class.java).configureEach { publication ->
            if (extension.groupId.isNotBlank()) publication.groupId = extension.groupId
            if (extension.version.isNotBlank()) publication.version = extension.version
            PomGenerator(project.providers, extension.metadata, publication.artifactId).apply(publication.pom)
            extension.publicationCustomizers.forEach { customize -> publication.customize() }
        }
    }

    private fun registerCentralRepository(publishing: PublishingExtension) {
        if (PublishTarget.MAVEN_CENTRAL !in extension.targets.enabled) return

        if (extension.isSnapshot) {
            // Snapshots deploy straight to Central's snapshot repo over plain
            // HTTP PUT via maven-publish - no staging/bundle/validate step.
            publishing.repositories.maven { repo ->
                repo.name = "CentralSnapshots"
                repo.url = project.uri(CENTRAL_SNAPSHOTS_URL)
                repo.credentials { creds ->
                    creds.username = credentials.requireCentralUsername(project.path)
                    creds.password = credentials.requireCentralPassword(project.path)
                }
            }
        } else {
            publishing.repositories.maven { repo ->
                repo.name = "CentralStaging"
                repo.url = project.uri(project.layout.buildDirectory.dir("publisher/central-staging").get())
            }
        }
    }

    private fun registerGithubRepository(publishing: PublishingExtension) {
        if (PublishTarget.GITHUB_PACKAGES !in extension.targets.enabled) return

        val repoFullName = extension.targets.githubRepoOverride
            ?: extension.metadata.repoRef?.fullName
            ?: throw GradleException(
                "targets { githubPackages() } needs a repo: either metadata { syncFrom(github(\"owner/repo\")) } " +
                    "or targets { githubPackages(repo = \"owner/repo\") } on '${project.path}'."
            )
        GithubPackagesClient(project, repoFullName, credentials).register()
    }
}
