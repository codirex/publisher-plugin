package org.codirex.publisher.dsl

/** `signing { requireGpg() }` - fails the build early if no GPG key can be resolved. */
class SigningDsl {
    var gpgRequired: Boolean = false
        private set

    fun requireGpg() {
        gpgRequired = true
    }
}
