package org.codirex.publisher.credentials

import java.util.Base64

data class CentralCredentials(
    val username: String,
    val password: String
) {
    /**
     * Central Portal's Publisher API wants `Authorization: Bearer <base64(username:password)>` -
     * note it's the Bearer scheme even though the payload is a Basic-style base64 pair.
     */
    val authorizationHeader: String
        get() = "Bearer " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())
}
