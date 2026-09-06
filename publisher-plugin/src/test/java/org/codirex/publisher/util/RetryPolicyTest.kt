package org.codirex.publisher.util

import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RetryPolicyTest {

    @Test
    fun `returns immediately on first success`() = runTest {
        var calls = 0
        val result = retryWithBackoff { calls++; "ok" }
        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun `retries on IOException then succeeds`() = runTest {
        var calls = 0
        val result = retryWithBackoff(maxAttempts = 3, initialDelayMs = 1) {
            calls++
            if (calls < 3) throw IOException("boom")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, calls)
    }

    @Test
    fun `gives up after maxAttempts and rethrows the real exception`() = runTest {
        var calls = 0
        assertFailsWith<IOException> {
            retryWithBackoff(maxAttempts = 2, initialDelayMs = 1) {
                calls++
                throw IOException("still broken")
            }
        }
        assertEquals(2, calls)
    }

    @Test
    fun `does not retry when shouldRetryException says no`() = runTest {
        var calls = 0
        assertFailsWith<IllegalStateException> {
            retryWithBackoff<Unit>(
                maxAttempts = 5,
                initialDelayMs = 1,
                shouldRetryException = { false }
            ) {
                calls++
                throw IllegalStateException("not retryable, e.g. a 4xx")
            }
        }
        assertEquals(1, calls)
    }

    @Test
    fun `retries when the result itself is flagged retryable`() = runTest {
        var calls = 0
        val result = retryWithBackoff(
            maxAttempts = 3,
            initialDelayMs = 1,
            shouldRetryResult = { it < 0 }
        ) {
            calls++
            if (calls < 2) -1 else 42
        }
        assertEquals(42, result)
        assertEquals(2, calls)
    }
}
