package org.codirex.publisher.credentials

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

/**
 * Resolves publishing credentials from, in order, environment variables then
 * Gradle properties (`gradle.properties`, `-P`, or `ORG_GRADLE_PROJECT_*` env
 * vars, which Gradle maps to properties automatically).
 *
 * Built on [ProviderFactory], not [org.gradle.api.Project]. Every value here
 * eventually gets read from inside a Task's `@TaskAction`, and Configuration
 * Cache disallows `Task.project` access at execution time - a `Project`-held
 * resolver used from a task field was exactly that bug in 1.1.0.
 * `ProviderFactory.environmentVariable`/`.gradleProperty` are the sanctioned,
 * CC-tracked way to carry a configuration-time-resolved value safely across
 * to execution time: wire the `Provider` into a task's `Property` at
 * configuration time (`task.githubToken.set(resolver.githubToken())`) and
 * read it with `.get()`/`.getOrNull()` inside `@TaskAction` - no `Project`
 * reference ever needs to survive into the task's serialized state.
 */
class CredentialResolver(private val providers: ProviderFactory) {

    fun githubUsername(): Provider<String> = firstNonBlank("GITHUB_ACTOR", "githubUsername")
    fun githubToken(): Provider<String> = firstNonBlank("GITHUB_TOKEN", "githubToken", "gpr.token")
    fun centralUsername(): Provider<String> = firstNonBlank("CENTRAL_USERNAME", "centralUsername", "mavenCentralUsername")
    fun centralPassword(): Provider<String> = firstNonBlank("CENTRAL_PASSWORD", "centralPassword", "mavenCentralPassword")
    fun signingKey(): Provider<String> = firstNonBlank("SIGNING_KEY", "signingKey")
    fun signingPassword(): Provider<String> = firstNonBlank("SIGNING_PASSWORD", "signingPassword")

    /** Lazy presence checks - safe to wire into a `Property<Boolean>` task input without resolving eagerly. */
    fun hasGithub(): Provider<Boolean> = githubUsername().presentAnd(githubToken())
    fun hasCentral(): Provider<Boolean> = centralUsername().presentAnd(centralPassword())
    fun hasSigningKey(): Provider<Boolean> = signingKey().map { it.isNotBlank() }.orElse(false)

    /**
     * Eager variants for configuration-time-only call sites (never from a
     * `@TaskAction`) that need the real value right now with a clear error
     * if it's missing - e.g. wiring a repository's credentials block, which
     * Gradle's own API only accepts as plain `String`, not `Provider`.
     */
    fun requireGithubUsername(contextPath: String) =
        requireValue(githubUsername(), "GitHub Packages username", contextPath, "GITHUB_ACTOR env var", "githubUsername gradle property")

    fun requireGithubToken(contextPath: String) =
        requireValue(githubToken(), "GitHub Packages token", contextPath, "GITHUB_TOKEN env var", "githubToken/gpr.token gradle property")

    fun requireCentralUsername(contextPath: String) =
        requireValue(centralUsername(), "Central Portal username", contextPath, "CENTRAL_USERNAME env var", "centralUsername gradle property")

    fun requireCentralPassword(contextPath: String) =
        requireValue(centralPassword(), "Central Portal password", contextPath, "CENTRAL_PASSWORD env var", "centralPassword gradle property")

    private fun requireValue(provider: Provider<String>, what: String, contextPath: String, vararg sources: String): String =
        provider.orNull?.takeIf { it.isNotBlank() }
            ?: throw org.gradle.api.GradleException(
                "Missing $what for '$contextPath'. Set one of: ${sources.joinToString()}."
            )

    private fun Provider<String>.presentAnd(other: Provider<String>): Provider<Boolean> =
        flatMap { a -> other.map { b -> a.isNotBlank() && b.isNotBlank() } }.orElse(false)

    private fun firstNonBlank(vararg keys: String): Provider<String> =
        keys.map { key -> providers.environmentVariable(key).orElse(providers.gradleProperty(key)) }
            .reduce { acc, next -> acc.orElse(next) }
}
