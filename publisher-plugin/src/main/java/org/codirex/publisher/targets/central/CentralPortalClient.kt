package org.codirex.publisher.targets.central

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.codirex.publisher.credentials.CentralCredentials
import org.codirex.publisher.util.retryWithBackoff
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Thin client for the Sonatype Central Publisher Portal API
 * (https://central.sonatype.com/api-doc). Endpoint paths here reflect the
 * documented API as of this writing; Sonatype has changed this API before,
 * so double-check against their current docs if uploads start failing.
 *
 * Every call is wrapped in [retryWithBackoff]: transient network failures
 * (IOException) and 5xx server responses are retried with exponential
 * backoff; 4xx responses (bad credentials, bad request) are not, since
 * retrying those just wastes time on a failure that won't self-heal.
 */
class CentralPortalClient(
    private val credentials: CentralCredentials,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
) : CentralClient {
    private val baseUrl = "https://central.sonatype.com/api/v1/publisher"

    override suspend fun uploadBundle(bundle: File, publishingType: String): String {
        val boundary = "----CodirexPublisher${System.currentTimeMillis()}"
        val body = multipartBody(bundle, boundary)

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/upload?publishingType=$publishingType"))
            .header("Authorization", credentials.authorizationHeader)
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .timeout(Duration.ofMinutes(5))
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()

        val response = retryWithBackoff(
            shouldRetryResult = { it.statusCode() >= 500 }
        ) {
            withContext(Dispatchers.IO) {
                httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            }
        }
        check(response.statusCode() in 200..299) {
            "Central Portal upload failed (${response.statusCode()}): ${response.body()}"
        }
        return response.body().trim() // API returns the deployment id as plain text
    }

    override suspend fun deploymentStatus(deploymentId: String): DeploymentState {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/status?id=$deploymentId"))
            .header("Authorization", credentials.authorizationHeader)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()

        val response = retryWithBackoff(
            shouldRetryResult = { it.statusCode() >= 500 }
        ) {
            withContext(Dispatchers.IO) {
                httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            }
        }
        if (response.statusCode() !in 200..299) return DeploymentState.UNKNOWN

        val state = Regex("\"deploymentState\"\\s*:\\s*\"(\\w+)\"")
            .find(response.body())?.groupValues?.get(1)
        return runCatching { DeploymentState.valueOf(state ?: "UNKNOWN") }
            .getOrDefault(DeploymentState.UNKNOWN)
    }

    private fun multipartBody(file: File, boundary: String): ByteArray {
        val header = (
            "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"bundle\"; filename=\"${file.name}\"\r\n" +
                "Content-Type: application/zip\r\n\r\n"
        ).toByteArray()
        val footer = "\r\n--$boundary--\r\n".toByteArray()
        return header + file.readBytes() + footer
    }
}
