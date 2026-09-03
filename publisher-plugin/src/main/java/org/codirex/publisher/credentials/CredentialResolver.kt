package org.codirex.publisher.credentials

import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * Resolves publishing credentials, checking (in order) environment variables
 * then Gradle properties. `ORG_GRADLE_PROJECT_<name>` env vars are already
 * mapped to project properties by Gradle itself, so callers get that for
 * free by putting the Gradle-property name second.
 */
class CredentialResolver(private val project: Project) {

    fun resolveGithub(): GithubCredentials? {
        val username = firstNonBlank("GITHUB_ACTOR", "githubUsername")
        val token = firstNonBlank("GITHUB_TOKEN", "githubToken", "gpr.token")
        return if (username != null && token != null) GithubCredentials(username, token) else null
    }

    fun resolveCentral(): CentralCredentials? {
        val username = firstNonBlank("CENTRAL_USERNAME", "centralUsername", "mavenCentralUsername")
        val password = firstNonBlank("CENTRAL_PASSWORD", "centralPassword", "mavenCentralPassword")
        return if (username != null && password != null) CentralCredentials(username, password) else null
    }

    fun resolveSigningKey(): String? = firstNonBlank("SIGNING_KEY", "signingKey")
    fun resolveSigningPassword(): String? = firstNonBlank("SIGNING_PASSWORD", "signingPassword")

    fun requireGithub(): GithubCredentials = resolveGithub()
        ?: throw GradleException(
            "Missing GitHub Packages credentials for '${project.path}'. Set GITHUB_ACTOR/GITHUB_TOKEN " +
                "env vars, or githubUsername/githubToken in gradle.properties."
        )

    fun requireCentral(): CentralCredentials = resolveCentral()
        ?: throw GradleException(
            "Missing Maven Central credentials for '${project.path}'. Set CENTRAL_USERNAME/CENTRAL_PASSWORD " +
                "env vars, or centralUsername/centralPassword in gradle.properties."
        )

    private fun firstNonBlank(vararg keys: String): String? {
        for (key in keys) {
            System.getenv(key)?.takeIf { it.isNotBlank() }?.let { return it }
            (project.findProperty(key) as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }
}
