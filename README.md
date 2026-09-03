# Codirex Publisher

**Zero-config Maven Central & GitHub Packages publishing for Gradle.**

Codirex Publisher is a Gradle plugin that turns a handful of common publishing chores — component detection, POM metadata, GPG signing, credential lookup, and the Sonatype Central Portal's upload/validate/publish dance — into a single declarative block. Point it at your GitHub repo, pick your targets, and run `./gradlew publishAll`.

```kotlin
publisher {
    identity("org.codirex", "axiom", "1.0.0")
    metadata { syncFrom(github("codirex/axiom")) }
    components.autoDetect()
    credentials.autoDetect()
    signing.requireGpg()
    targets { mavenCentral(); githubPackages() }
}
```

[![Maven Central](https://img.shields.io/maven-central/v/org.codirex.publisher/publisher-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.codirex.publisher/publisher-plugin)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Gradle](https://img.shields.io/badge/Gradle-8.14%2B-02303A.svg?logo=gradle)](https://gradle.org)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)

---

## Table of Contents

- [Why Codirex Publisher](#why-codirex-publisher)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Multi-Module Ecosystems](#multi-module-ecosystems)
- [DSL Reference](#dsl-reference)
- [Publish Targets](#publish-targets)
- [Tasks](#tasks)
- [Credentials](#credentials)
- [Dry-Run Mode](#dry-run-mode)
- [Component Detection](#component-detection)
- [Metadata Overrides From JSON](#metadata-overrides-from-json)
- [Reliability & Networking](#reliability--networking)
- [Publishing This Plugin Itself](#publishing-this-plugin-itself)
- [Troubleshooting](#troubleshooting)
- [Project Structure](#project-structure)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

---

## Why Codirex Publisher

Publishing a Kotlin/Java/Android library to Maven Central "properly" means wiring up, by hand, in every module:

- `maven-publish` + a `MavenPublication` `from(components["..."])`
- sources + javadoc jars
- a POM with name/description/url/license/developers/scm
- the `signing` plugin, in-memory PGP keys, and `sign(publications)`
- credential lookup that doesn't hardcode secrets into `build.gradle.kts`
- a repository per target — GitHub Packages, Central staging, Central snapshots
- Central's newer Portal API: bundle the staging repo into a zip, upload it, poll until it validates and publishes

None of that is hard on its own. Repeating it correctly across a dozen modules — and keeping it correct as Sonatype's API evolves — is where it gets tedious and error-prone. Codirex Publisher does all of it once, so a module's build script only has to say *what* it is, not *how* to publish it.

## Features

- **Component auto-detection** — Android Library, plain Java/Kotlin library, or Kotlin Multiplatform; sources/javadoc jars wired automatically where that's this plugin's job.
- **GitHub metadata sync** — description, project URL, and repo URL pulled from the GitHub API so you don't duplicate them in every `build.gradle.kts`.
- **Three publish targets** — GitHub Packages, Maven Central (release *and* `-SNAPSHOT`), and `mavenLocal()` for local testing, mixed and matched per module.
- **Ecosystem mode** — configure an entire multi-module repo's publishing from the root `build.gradle.kts` in a dozen lines.
- **Dry-run mode** — generate the POM, run signing, build the upload bundle, and validate configuration without ever touching the network.
- **Resilient networking** — every GitHub/Central Portal call retries transient failures with exponential backoff.
- **Testable core** — the Central Portal client sits behind a `CentralClient` interface, so the deploy/poll logic can be unit-tested against a fake.
- **One readiness check** — `checkPublisherConfig` tells you exactly what's missing (identity, credentials, signing key) before you waste a publish attempt.

## Requirements

- Gradle 8.14+ (uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS`-style centralized repo management and the modern `WorkerExecutor` API)
- JDK 17 toolchain
- One of: Android Gradle Plugin (`com.android.library`), `java-library`, or `org.jetbrains.kotlin.multiplatform` applied to the module
- A Sonatype Central Portal account with a verified namespace, if publishing to Maven Central
- A GPG key, if `signing.requireGpg()` is used

## Installation

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral() // required — this is where the plugin's marker artifact resolves from
        google()       // only needed if any module is an Android library
    }
}
```

```kotlin
// module build.gradle.kts
plugins {
    id("com.android.library") // or java-library / kotlin("multiplatform")
    id("org.codirex.publisher") version "1.0.0"
}
```

## Quick Start

```kotlin
publisher {
    // 1. Maven coordinates
    identity("org.codirex", "axiom", "1.0.0")

    // 2. Pull description, project URL, and SCM URL from GitHub automatically
    metadata {
        syncFrom(github("codirex/axiom"))
        license = Licenses.APACHE_2_0
    }

    // 3. Detects Android Library vs. java-library vs. Kotlin Multiplatform,
    //    and generates sources + javadoc jars where that's this plugin's job
    components.autoDetect()

    // 4. Looks for credentials in env vars, then gradle.properties
    credentials.autoDetect()

    // 5. Signs every publication with an in-memory PGP key
    signing.requireGpg()

    // 6. Where to publish
    targets {
        mavenCentral()
        githubPackages()
    }
}
```

```bash
./gradlew checkPublisherConfig   # verify identity/credentials/signing before you commit to a real publish
./gradlew publishAll             # publish to every enabled target
```

## Multi-Module Ecosystems

Configure an entire repo's worth of modules once, from the root project:

```kotlin
// root build.gradle.kts
publisherEcosystem {
    baseGroupId = "org.codirex"
    sharedLicense = Licenses.APACHE_2_0
    credentials.autoDetect()

    autoPublishModules { module ->
        // applies org.codirex.publisher to every subproject and infers its repo
        syncFrom(github("codirex/${module.name}"))

        targets {
            mavenCentral()
            githubPackages()
        }
    }
}
```

Each subproject still gets its own `PublisherExtension` — `autoPublishModules` just seeds `baseGroupId`, `sharedLicense`, and the credentials strategy before handing control to your per-module block, so an individual module can still override anything it needs to.

## DSL Reference

### `identity(groupId, artifactId, version)`

Sets the Maven coordinates. Required — nothing else in the plugin activates until this is set (`isConfigured()` gates the rest of `PublisherPlugin`).

### `metadata { }`

| Member | Description |
|---|---|
| `syncFrom(github("owner/repo"))` | Registers a GitHub repo to pull metadata from. The actual network call is lazy — it only fires the first time the POM is generated. |
| `license` | A `License` value — see [`Licenses`](#built-in-licenses) below, or construct your own: `License(name, url, spdxId)`. |
| `description`, `projectUrl`, `scmUrl` | Explicit overrides. Anything left blank falls back to whatever `syncFrom` retrieves. |
| `developer(id, name, email = null, url = null, roles = emptyList())` | Adds one `<developer>` entry to the POM. Call multiple times for multiple developers. |
| `loadOverridesFrom(file)` | Loads description/URLs/license/developers from a JSON file — see [Metadata Overrides](#metadata-overrides-from-json). |

#### Built-in licenses

`Licenses.APACHE_2_0`, `Licenses.MIT`, `Licenses.GPL_3_0`, `Licenses.LGPL_2_1`, `Licenses.BSD_3_CLAUSE`.

### `components { }`

`autoDetect()` — the only current option. Delegates to `ComponentDetector` (see [Component Detection](#component-detection)).

### `credentials { }`

`autoDetect()` — enables `CredentialResolver`'s env-var → `gradle.properties` lookup chain. See [Credentials](#credentials).

### `signing { }`

`requireGpg()` — applies the `signing` plugin and signs every publication. Uses in-memory PGP keys resolved via `CredentialResolver` if available, otherwise falls back to the `signing` plugin's own gpg-agent/keyring lookup.

### `targets { }`

| Function | Target |
|---|---|
| `mavenCentral()` | Maven Central, via the Sonatype Central Portal (release) or the snapshot repository (`-SNAPSHOT` versions). |
| `githubPackages()` | `https://maven.pkg.github.com/<owner>/<repo>`, inferred from `metadata { syncFrom(...) }`. |
| `mavenLocal()` | `~/.m2/repository`, for testing in other local projects before a real release. |

### `dryRun`

```kotlin
publisher {
    dryRun = true
}
```

Generates the POM, runs signing, and (for Central) builds `bundle.zip` — but skips every real network upload. See [Dry-Run Mode](#dry-run-mode).

## Publish Targets

### GitHub Packages

Registers a standard Maven repository at `https://maven.pkg.github.com/<owner>/<repo>` using credentials from `CredentialResolver`. The actual push is Gradle's own auto-generated `publish...ToGithubPackagesRepository` task(s) — `publishToGithubPackages` just depends on whichever of those exist (matched by repository name, so this works for single- and multi-publication components alike).

### Maven Central — release versions

1. Publishes locally to a staging directory (`build/publisher/central-staging`).
2. `BundleGenerator` zips that directory into `bundle.zip` per the Central Portal's required layout.
3. `CentralUploadWorkAction` (a Gradle Worker) uploads the bundle via the Central Portal API and polls the deployment status until it's `PUBLISHED` or `FAILED`.

### Maven Central — snapshot versions

If `identity(...)`'s version ends in `-SNAPSHOT`, the plugin skips the bundle/validate/publish flow entirely and pushes straight to Central's snapshot repository (`https://central.sonatype.com/repository/maven-snapshots/`) via a plain `maven-publish` repository — that's how Sonatype's snapshot API works; there's no staging step for snapshots.

> Sonatype doesn't publish a Gradle plugin marker for snapshot versions. If you need `plugins { id("...") version "...-SNAPSHOT" }` to resolve, add a `pluginManagement.resolutionStrategy.eachPlugin { }` override on the consuming project.

### Maven Local

`targets { mavenLocal() }` adds a `publishToLocalMaven` task that depends on Gradle's built-in `publish...ToMavenLocal` task(s) — useful for `mavenLocal()`-testing a not-yet-released version in a sibling project.

## Tasks

| Task | Description |
|---|---|
| `checkPublisherConfig` | Validates identity, targets, credentials, and signing; fails with a specific report if anything's missing. |
| `publishToGithubPackages` | Publishes to GitHub Packages, if that target is enabled. |
| `publishToMavenCentral` | Bundles and uploads to Maven Central (release), or confirms the direct snapshot push (snapshot). |
| `publishToLocalMaven` | Publishes to `~/.m2/repository`, if that target is enabled. |
| `publishAll` | Runs all three of the above. |

## Credentials

`CredentialResolver` checks environment variables first, then Gradle properties (`gradle.properties`, `-P`, or `ORG_GRADLE_PROJECT_*` env vars, which Gradle maps to properties automatically).

| Purpose | Environment variable(s) | Gradle property |
|---|---|---|
| GitHub Packages username | `GITHUB_ACTOR` | `githubUsername` |
| GitHub Packages token | `GITHUB_TOKEN` | `githubToken` / `gpr.token` |
| Central Portal username | `CENTRAL_USERNAME` | `centralUsername` / `mavenCentralUsername` |
| Central Portal password | `CENTRAL_PASSWORD` | `centralPassword` / `mavenCentralPassword` |
| GPG signing key (ASCII-armored) | `SIGNING_KEY` | `signingKey` |
| GPG signing key passphrase | `SIGNING_PASSWORD` | `signingPassword` |

Central Portal usernames/passwords are **User Tokens** generated at `central.sonatype.com/usertoken` — not your account login.

## Dry-Run Mode

```kotlin
publisher {
    dryRun = true
}
```

With `dryRun` enabled:

- `checkPublisherConfig` still validates identity and the signing key, but **doesn't** require GitHub/Central credentials — the point of a dry run is being able to test without real production secrets on hand.
- The POM is generated and every publication is signed, exactly as normal.
- For Maven Central release versions, `bundle.zip` is still built (so you can inspect it) — only the actual Central Portal upload is skipped.
- For GitHub Packages and Central snapshots, the real network push is skipped entirely (there's no local-only equivalent for a direct HTTP `PUT`).

## Component Detection

| Plugin applied | `ComponentType` | What Codirex Publisher configures |
|---|---|---|
| `com.android.library` | `ANDROID_LIBRARY` | `android.publishing.singleVariant("release") { withSourcesJar(); withJavadocJar() }`, publication `from(components["release"])` |
| `java-library` / `java` | `JAVA_LIBRARY` | `JavaPluginExtension.withSourcesJar()` / `withJavadocJar()`, publication `from(components["java"])` |
| `org.jetbrains.kotlin.multiplatform` | `KOTLIN_MULTIPLATFORM` | Brands every publication the `kotlin-multiplatform` plugin already creates (one per target) with identity + POM metadata. Per-target sources jars still follow that target's own `kotlin { jvm { withSourcesJar() } }`-style configuration — this plugin doesn't override that. |
| none of the above | `UNKNOWN` | Build fails fast with a clear message: apply one of the plugins above before `org.codirex.publisher`. |

## Metadata Overrides From JSON

For teams that want to keep publishing metadata centralized rather than duplicated across every module's `build.gradle.kts`:

```json
{
  "description": "A from-scratch structured-concurrency library for Kotlin.",
  "projectUrl": "https://github.com/codirex/clotho",
  "scmUrl": "https://github.com/codirex/clotho.git",
  "license": { "name": "Apache License, Version 2.0", "url": "https://www.apache.org/licenses/LICENSE-2.0.txt", "spdxId": "Apache-2.0" },
  "developers": [
    { "id": "ahmed", "name": "Ahmed", "email": "ahmed@codirex.org", "roles": ["Owner"] }
  ]
}
```

```kotlin
metadata {
    loadOverridesFrom(file("$rootDir/publishing-metadata.json"))
}
```

Anything present in the file always wins over whatever `syncFrom(github(...))` would have filled in — loaded values are set directly, and GitHub sync only fills in fields that are still blank. XML isn't supported, only JSON; open an issue if you need it.

## Reliability & Networking

Every GitHub API and Central Portal HTTP call goes through `retryWithBackoff`: transient failures (`IOException`) and HTTP 5xx responses are retried with exponential backoff (4 attempts by default); HTTP 4xx responses (bad credentials, malformed request) are not retried, since waiting doesn't fix those.

The Central Portal upload and deployment-status poll run inside a Gradle `WorkAction` (`CentralUploadWorkAction`, via `WorkerExecutor.noIsolation()`), and the poll loop suspends with coroutine `delay()` rather than blocking on `Thread.sleep()`. Worth being upfront about what this does and doesn't buy: Gradle task actions are synchronous by contract, so `publishToMavenCentral` still doesn't *return* until the deployment finishes — what this design gives you is a cleaner backoff/retry implementation and keeps the wait off the main configuration thread, not true non-blocking concurrency.

`CentralDeployManager` depends on the `CentralClient` interface rather than `CentralPortalClient` directly, so the deploy/poll logic can be exercised in unit tests against a fake implementation without making real HTTP calls.

## Publishing This Plugin Itself

`org.codirex.publisher` can't apply its own `publisher { }` DSL to itself for its very first release — there's nothing published yet to resolve `id("org.codirex.publisher")` from. The `publisher-plugin` module's own `build.gradle.kts` therefore configures plain `maven-publish` + `signing` directly for that one-time bootstrap. After `1.0.0` is live on Maven Central, every other `org.codirex` module (and future releases of this plugin itself) can use the real DSL.

## Troubleshooting

**`Task with path 'publish...Repository' not found`**
A target's repository was never registered because it wasn't enabled in `targets { }`. Double check `targets { mavenCentral(); githubPackages() }` matches what you're trying to run.

**Central Portal upload fails with `401`**
Central Portal's Publisher API expects `Authorization: Bearer <base64(user:pass)>` — if you're calling it directly rather than through this plugin, make sure you're using `Bearer`, not `Basic`, with that same base64 payload.

**Central validation rejects the bundle: "missing sources/javadoc jar"**
Make sure `components.autoDetect()` is set — it's what wires up `withSourcesJar()`/`withJavadocJar()` for Android/Java libraries. Kotlin Multiplatform projects need this configured per-target instead (see [Component Detection](#component-detection)).

**Central validation rejects the bundle: missing checksums**
Gradle's `maven-publish` doesn't generate `.md5`/`.sha1` sidecar files when publishing to a local (`file://`) repository — which the Central staging repo is, before bundling. Generate them over the staging directory before zipping if you're bundling by hand instead of using `publishToMavenCentral`.

**`plugins { id("org.codirex.publisher") }` doesn't resolve for consumers**
Make sure `mavenCentral()` is listed under `pluginManagement.repositories` in the consumer's `settings.gradle.kts` — Gradle's plugin marker resolution works from any Maven repo listed there, not only the Gradle Plugin Portal.

**Namespace verification fails on the Central Portal**
Your groupId's root must match a domain you can prove ownership of via a DNS TXT record (e.g. `org.codirex` → `codirex.org`), or you'll need to use an `io.github.<username>`-style namespace instead, which verifies automatically via GitHub login.

## Project Structure

```
publisher-plugin/
└── src/main/java/org/codirex/publisher/
    ├── PublisherPlugin.kt          # entry point; wires everything together
    ├── dsl/                        # publisher { } / publisherEcosystem { } DSL
    ├── engine/                     # ComponentDetector, MavenPublishOrchestrator, SigningConfigurator
    ├── metadata/                   # GithubSyncEngine, PomGenerator
    ├── credentials/                # CredentialResolver + credential data classes
    ├── targets/
    │   ├── github/                 # GithubPackagesClient
    │   └── central/                # CentralClient, CentralPortalClient, CentralDeployManager, BundleGenerator
    ├── task/                       # checkPublisherConfig, publishToGithubPackages, publishToMavenCentral, publishToLocalMaven, publishAll
    └── util/                       # retryWithBackoff
```

## Roadmap

- [ ] XML support for `loadOverridesFrom` (JSON only today)
- [ ] Per-target `withSourcesJar()`/`withJavadocJar()` automation for Kotlin Multiplatform
- [ ] Gradle Plugin Portal publishing, in addition to Maven Central
- [ ] Unit test suite exercising `CentralDeployManager` against a fake `CentralClient`

## Contributing

Issues and pull requests are welcome. If you're proposing a new publish target or DSL surface, please open an issue first to discuss the shape of it — the goal of this plugin is to stay a thin, predictable layer over `maven-publish`, not to grow into its own publishing framework.

## License

Apache License, Version 2.0 — see [LICENSE](LICENSE).
