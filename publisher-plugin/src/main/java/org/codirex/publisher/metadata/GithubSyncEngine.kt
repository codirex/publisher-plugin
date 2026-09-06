package org.codirex.publisher.metadata

import org.json.JSONObject
import java.io.IOException
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
 * [fetch] is deliberately plain, blocking, coroutine-free Kotlin - no
 * `runBlocking`, no `suspend`. This matters more than it looks like it
 * should: [org.codirex.publisher.metadata.PomGenerator] wires this call
 * behind a lazy `Provider` set on `MavenPom.description`/`.url`/`.scm.url`,
 * and Gradle's `GenerateMavenPom` task caches that Provider's computed
 * value internally (`Cached.Deferred`) as part of the state Configuration
 * Cache serializes. In 1.2.0-1.2.2 this method used
 * `runBlocking { retryWithBackoff { ... } }`; that pulled Kotlin's
 * coroutine continuation "spilling" machinery into the object graph CC
 * tries to serialize, and CC cannot handle it - the exact failure was
 * `Configuration cache state could not be cached: ... Cached$Deferred ...
 * kotlin/coroutines/jvm/internal/SpillingKt`. Coroutines are fine inside a
 * Gradle `WorkAction.execute()` (real execution-time code, see
 * [org.codirex.publisher.targets.central.CentralUploadWorkAction]); they
 * are not safe anywhere reachable from a lazy `Provider`/`Property` that
 * Gradle itself memoizes. Retries here use a plain blocking loop instead.
 */
class GithubSyncEngine(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
) {

    fun fetch(ref: GithubRepoRef): RepoMetadata {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create("https://api.github.com/repos/${ref.fullName}"))
            .header("Accept", "application/vnd.github+json")
            .timeout(Duration.ofSeconds(15))
            .GET()

        System.getenv("GITHUB_TOKEN")?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }
        val request = requestBuilder.build()

        val response = retryBlocking(
            shouldRetryResult = { it.statusCode() >= 500 }
        ) {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }
        check(response.statusCode() == 200) {
            "GitHub API request for ${ref.fullName} failed with status ${response.statusCode()}: ${response.body()}"
        }

        val json = JSONObject(response.body())
        val license = json.optJSONObject("license")
        return RepoMetadata(
            description = json.optNullableString("description"),
            htmlUrl = json.optNullableString("html_url"),
            cloneUrl = json.optNullableString("clone_url"),
            licenseSpdxId = license?.optNullableString("spdx_id"),
            defaultBranch = json.optNullableString("default_branch")
        )
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    /** Plain blocking exponential-backoff retry - see the class kdoc for why this can't be `suspend`. */
    private fun <T> retryBlocking(
        maxAttempts: Int = 4,
        initialDelayMs: Long = 500,
        maxDelayMs: Long = 8_000,
        factor: Double = 2.0,
        shouldRetryResult: (T) -> Boolean = { false },
        shouldRetryException: (Throwable) -> Boolean = { it is IOException },
        block: () -> T
    ): T {
        var delayMs = initialDelayMs
        repeat(maxAttempts) { attempt ->
            val isLastAttempt = attempt == maxAttempts - 1
            try {
                val result = block()
                if (isLastAttempt || !shouldRetryResult(result)) return result
            } catch (e: Throwable) {
                if (isLastAttempt || !shouldRetryException(e)) throw e
            }
            Thread.sleep(delayMs)
            delayMs = (delayMs * factor).toLong().coerceAtMost(maxDelayMs)
        }
        error("unreachable")
    }
}
