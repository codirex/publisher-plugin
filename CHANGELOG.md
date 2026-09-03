# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.0] - 2026-09-03

### Added
- `publisher { dryRun = true }` flag — generates the POM, runs signing, and (for release versions) builds the Maven Central upload bundle, without performing any real network publish. `checkPublisherConfig` skips requiring GitHub/Central credentials in this mode.
- `PublishTarget.MAVEN_LOCAL` / `targets { mavenLocal() }`, with a new `publishToLocalMaven` task for publishing to `~/.m2/repository`.
- Automatic `-SNAPSHOT` handling: versions ending in `-SNAPSHOT` publish directly to Central's snapshot repository (`https://central.sonatype.com/repository/maven-snapshots/`) instead of going through the bundle/validate/publish flow.
- Kotlin Multiplatform support: `ComponentDetector` recognizes `org.jetbrains.kotlin.multiplatform`, and `MavenPublishOrchestrator` configures identity and POM metadata on every publication the `kotlin-multiplatform` plugin creates.
- `CentralClient` interface, implemented by `CentralPortalClient` — isolates the Sonatype-specific HTTP/JSON handling so `CentralDeployManager` can be unit-tested against a fake.
- `org.codirex.publisher.util.retryWithBackoff` — exponential-backoff retry, applied to every GitHub API and Central Portal HTTP call. Retries `IOException` and HTTP 5xx responses; does not retry HTTP 4xx.
- `metadata { developer(id, name, email = null, url = null, roles = emptyList()) }` for adding one or more developers/contributors to the generated POM.
- `metadata { loadOverridesFrom(file) }` — loads description, project/SCM URLs, license, and developers from a JSON file, taking precedence over anything GitHub sync would have filled in.

### Changed
- Central Portal upload and deployment-status polling now run inside a Gradle `WorkAction` (`CentralUploadWorkAction`, submitted via `WorkerExecutor.noIsolation()`); the poll loop suspends with coroutine `delay()` instead of blocking on `Thread.sleep()`.
- `publishToGithubPackages`, `publishToMavenCentral`, and `publishToLocalMaven` now depend on Gradle's auto-generated publish tasks by matching **repository name** rather than a hardcoded publication name, so they work correctly for component types that produce more than one `MavenPublication` (e.g. Kotlin Multiplatform).
- `metadata.developers` changed from `List<Pair<String, String>>` to a proper `Developer` data class (`id`, `name`, `email`, `url`, `roles`).

### Fixed
- `CentralCredentials` sent `Authorization: Basic <token>` to the Central Portal API. The Portal's Publisher API actually requires the `Bearer` scheme for that same base64-encoded payload — every Central Portal call would have failed with `401 Unauthorized` until this was corrected.

## [1.0.0] - 2026-09-01

Initial release.

### Added
- `publisher { }` DSL: `identity(groupId, artifactId, version)`, `metadata { syncFrom(github(...)); license }`, `components.autoDetect()`, `credentials.autoDetect()`, `signing.requireGpg()`, `targets { mavenCentral(); githubPackages() }`.
- `publisherEcosystem { }` root-project DSL for configuring an entire multi-module repository's publishing at once, via `autoPublishModules { }`.
- `ComponentDetector` for `com.android.library` and `java-library`/`java` projects, auto-configuring sources and javadoc jars.
- `GithubSyncEngine` — pulls description, project URL, and clone URL from the public GitHub REST API for POM generation.
- `MavenPublishOrchestrator` — creates the `release` `MavenPublication` from the detected component and registers a repository per enabled target.
- `SigningConfigurator` — signs every publication using in-memory PGP keys resolved via `CredentialResolver`, falling back to the `signing` plugin's own keyring lookup.
- `CredentialResolver` — resolves GitHub, Central, and signing credentials from environment variables, then Gradle properties.
- Maven Central publishing via the Sonatype Central Portal API: local staging repository, `bundle.zip` generation (`BundleGenerator`), upload, and deployment-status polling (`CentralPortalClient`, `CentralDeployManager`).
- GitHub Packages publishing (`GithubPackagesClient`).
- Built-in license presets: `Licenses.APACHE_2_0`, `MIT`, `GPL_3_0`, `LGPL_2_1`, `BSD_3_CLAUSE`.
- Tasks: `checkPublisherConfig`, `publishToGithubPackages`, `publishToMavenCentral`, `publishAll`.

[Unreleased]: https://github.com/codirex/publisher/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/codirex/publisher/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/codirex/publisher/releases/tag/v1.0.0
