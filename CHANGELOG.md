# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.2.0] - 2026-09-04

A production-hardening pass: this release exists to make the plugin safe to run under Gradle's Configuration Cache and correct across plugin-application ordering, not just to add features.

### Fixed - Configuration Cache violations (breaking for internal APIs, not for the DSL)
- All four tasks (`checkPublisherConfig`, `publishToGithubPackages`, `publishToMavenCentral`, `publishToLocalMaven`) held `lateinit var publisherExtension: PublisherExtension` fields. `PublisherExtension` itself holds a `Project` reference, which Configuration Cache cannot serialize - every one of these tasks would have failed with `--configuration-cache` enabled. Rewritten as abstract Gradle tasks with managed `Property`/`SetProperty`/`DirectoryProperty`/`RegularFileProperty` inputs, resolved once at configuration time and carried across the CC boundary the way Gradle expects.
- `CredentialResolver` took a `Project` and was constructed and used *inside* `@TaskAction` methods - `Task.project` access at execution time is explicitly unsupported under Configuration Cache. Rewritten to take a `ProviderFactory` instead, returning lazy `Provider<String>`/`Provider<Boolean>` values that get wired into task `Property` inputs at configuration time.
- `BundleGenerator` took a `Project` for one thing (computing the output path) and was invoked from inside `PublishToCentralTask`'s `@TaskAction` - another `Task.project`-at-execution-time violation. Now a stateless `object` taking plain `File` in/out; the path is computed at configuration time and passed in as a `RegularFileProperty`.
- Central Portal upload/poll now happens entirely inside `CentralUploadWorkAction`'s `WorkParameters` (`DirectoryProperty`/`RegularFileProperty`/`Property<String>`) - no `Project`, no extension object, ever crosses into that execution-time code.

### Fixed - other real bugs
- **POM metadata was fetched from GitHub during every single Gradle invocation**, not just when actually publishing. `PomGenerator` called `metadata.resolve()` eagerly while configuring the `MavenPublication`, meaning `./gradlew tasks`, `./gradlew build`, or any other command would silently make a network call to GitHub's API as a side effect - and would hang/fail this project's configuration entirely if offline. `MetadataDsl` no longer holds an (unused) `Project` reference, and `PomGenerator` now wires `description`/`url`/`scm.url` through lazy `Provider`s, so the GitHub call only fires when `GenerateMavenPom` actually executes.
- **AGP variant/component preparation raced AGP's own internal setup.** `ComponentDetector.prepare()` ran inside this plugin's own `afterEvaluate`, which is ordering-dependent relative to AGP's *own* `afterEvaluate`-based variant wiring - a known source of "component 'release' not found"-style failures. Preparation now hooks `PluginContainer.withId("com.android.library"/"java")` at `apply()` time instead, firing immediately if the plugin's already applied or the moment it is, either way ahead of anything that depends on it.
- Switched from `com.android.build.gradle.LibraryExtension` (the legacy implementation class) to `com.android.build.api.dsl.LibraryExtension` (the interface Google's own docs use for this exact `publishing.singleVariant(...)` configuration).
- Central bundles were missing `.md5`/`.sha1` checksums for every artifact - Gradle's `maven-publish` doesn't generate them for local (`file://`) repositories (a known Gradle limitation, gradle/gradle#22482), so this was previously a manual step. `BundleGenerator` now generates any missing checksums before zipping.
- Removed a dead, unused `GithubCredentials` data class and an unused `Project` constructor parameter on `MetadataDsl`.

### Added
- `checkPublisherConfig` now validates POM completeness for Maven Central: a blank description/url, or zero `<developer>` entries, is caught here instead of surfacing as a Central validation rejection after a real upload attempt.
- `identity()` now rejects blank groupId/artifactId/version immediately with a clear error, instead of silently accepting them and failing confusingly later.
- `identity()` no-arg overload: `identity()` alone infers `(project.group, project.name, project.version)` when those are already set on the Gradle project.
- `metadata { developers(vararg Developer) }` as a bulk alternative to calling `developer(...)` repeatedly.
- `publisher { publication { /* MavenPublication.() -> Unit */ } }` - an escape hatch for anything this plugin doesn't model directly (e.g. attaching a Dokka javadoc jar), run after the plugin's own publication setup, on every publication (all of them, for Kotlin Multiplatform).
- `targets { mavenCentral(autoPublish = false) }` - uploads and validates but leaves the deployment `PENDING` for manual review/publish, instead of auto-publishing once validation passes.
- `targets { githubPackages(repo = "owner/repo") }` - explicit override so GitHub Packages doesn't require `metadata { syncFrom(...) }` if you don't want GitHub metadata sync at all.
- Real unit tests: `RetryPolicyTest`, `BundleGeneratorTest`, and `CentralDeployManagerTest` (against a fake `CentralClient` - exactly what pulling that interface out in 1.1.0 was for). Test setup switched to JUnit 5 (`kotlin-test-junit5` + `kotlinx-coroutines-test`, `useJUnitPlatform()`).

### Changed
- Internal-only signature changes (not part of the public `publisher { }`/`publisherEcosystem { }` DSL, so existing build scripts are unaffected): `CredentialResolver(Project)` → `CredentialResolver(ProviderFactory)`; `PomGenerator` takes a `ProviderFactory`; `BundleGenerator` is now a stateless `object` with a `(File, File)` signature instead of a `Project`-holding class; `MetadataDsl()` no longer takes a `Project`.
- Credential values on `PublishToCentralTask` are `@Internal`, not `@Input` - secrets shouldn't be hashed into a task's up-to-date/build-cache fingerprint. None of the four tasks declare `@OutputFile`/`@OutputDirectory`: they have real network side effects and must never be silently skipped by Gradle's up-to-date checking.

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

[Unreleased]: https://github.com/codirex/publisher/compare/v1.2.0...HEAD
[1.2.0]: https://github.com/codirex/publisher/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/codirex/publisher/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/codirex/publisher/releases/tag/v1.0.0
