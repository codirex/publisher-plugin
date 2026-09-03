package org.codirex.publisher.util

import kotlinx.coroutines.delay
import org.gradle.api.logging.Logging

/**
 * Generic exponential-backoff retry for suspendable network calls. Used by
 * [org.codirex.publisher.metadata.GithubSyncEngine] and
 * [org.codirex.publisher.targets.central.CentralPortalClient] so a flaky
 * connection or a slow-to-respond server doesn't fail an entire publish.
 *
 * Retries when [block] throws (if [shouldRetryException] agrees) or when it
 * returns a result [shouldRetryResult] flags as retryable (e.g. an HTTP 5xx).
 * Never retries on the last attempt - the original exception/result is
 * surfaced so callers see the real failure, not a generic timeout.
 */
suspend fun <T> retryWithBackoff(
    maxAttempts: Int = 4,
    initialDelayMs: Long = 500,
    maxDelayMs: Long = 8_000,
    factor: Double = 2.0,
    shouldRetryResult: (T) -> Boolean = { false },
    shouldRetryException: (Throwable) -> Boolean = { it is java.io.IOException },
    block: suspend (attempt: Int) -> T
): T {
    val logger = Logging.getLogger("org.codirex.publisher.retry")
    var delayMs = initialDelayMs

    repeat(maxAttempts) { attempt ->
        val isLastAttempt = attempt == maxAttempts - 1
        try {
            val result = block(attempt)
            if (isLastAttempt || !shouldRetryResult(result)) return result
            logger.info("Retrying (attempt ${attempt + 1}/$maxAttempts returned a retryable result)")
        } catch (e: Throwable) {
            if (isLastAttempt || !shouldRetryException(e)) throw e
            logger.info("Retrying after ${e::class.simpleName}: ${e.message} (attempt ${attempt + 1}/$maxAttempts)")
        }
        delay(delayMs)
        delayMs = (delayMs * factor).toLong().coerceAtMost(maxDelayMs)
    }
    error("unreachable") // repeat() always returns via the branches above
}
