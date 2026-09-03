package org.codirex.publisher.credentials

/** `credentials { autoDetect() }` - marks that CredentialResolver should search env/gradle.properties. */
class CredentialsDsl {
    var autoDetectEnabled: Boolean = false
        private set

    fun autoDetect() {
        autoDetectEnabled = true
    }
}
