package org.codirex.publisher.dsl

/** `components { autoDetect() }` - lets ComponentDetector pick AGP vs java-library. */
class ComponentsDsl {
    var autoDetectEnabled: Boolean = false
        private set

    fun autoDetect() {
        autoDetectEnabled = true
    }
}
