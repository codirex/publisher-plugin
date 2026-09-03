package org.codirex.publisher.metadata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.codirex.publisher.util.retryWithBackoff
import org.json.JSONObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class GithubRepoRef(val owner: String, val repo: String) {
    val fullName: String get() = "$owner/$repo"
}

data class RepoMetadata(
    val description: String?,
    val htmlUrl: String?,
    val cloneUrl: String?,
    val licenseSpdxId: String?,
    val defaultBranch: String?
)

/**
 * Pulls repo metadata from the public GitHub REST API. Works unauthenticated
 * for public repos; if a `GITHUB_TOKEN` env var is present it's sent along
 * to raise the (otherwise low) unauthenticated rate limit.
 *
 * [fetch] stays a plain blocking call (it's invoked from
 * [org.codirex.publisher.dsl.MetadataDsl.resolve], itself called from
 * synchronous Gradle configuration/POM-generation code) but internally
 * retries transient failures - IOExceptions and 5xx responses - with
 * exponential backoff via [retryWithBackoff]. A 404 (repo doesn't exist)
 * or 403 (rate-limited) is not retried; those need the user to fix
 * something, not wait it out.
 */
class GithubSyncEngine(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
) {

    fun fetch(ref: GithubRepoRef): RepoMetadata = runBlocking {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create("https://api.github.com/repos/${ref.fullName}"))
            .header("Accept", "application/vnd.github+json")
            .timeout(Duration.ofSeconds(15))
            .GET()

        System.getenv("GITHUB_TOKEN")?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }
        val request = requestBuilder.build()

        val response = retryWithBackoff(
            shouldRetryResult = { it.statusCode() >= 500 }
        ) {
            withContext(Dispatchers.IO) {
                httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            }
        }
        check(response.statusCode() == 200) {
            "GitHub API request for ${ref.fullName} failed with status ${response.statusCode()}: ${response.body()}"
        }

        val json = JSONObject(response.body())
        val license = json.optJSONObject("license")
        RepoMetadata(
            description = json.optNullableString("description"),
            htmlUrl = json.optNullableString("html_url"),
            cloneUrl = json.optNullableString("clone_url"),
            licenseSpdxId = license?.optNullableString("spdx_id"),
            defaultBranch = json.optNullableString("default_branch")
        )
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null
}
