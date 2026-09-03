package org.codirex.publisher.dsl

enum class PublishTarget { MAVEN_CENTRAL, GITHUB_PACKAGES, MAVEN_LOCAL }

/** `targets { mavenCentral(); githubPackages(); mavenLocal() }` */
class TargetsDsl {
    private val _enabled = linkedSetOf<PublishTarget>()
    val enabled: Set<PublishTarget> get() = _enabled

    fun mavenCentral() {
        _enabled += PublishTarget.MAVEN_CENTRAL
    }

    fun githubPackages() {
        _enabled += PublishTarget.GITHUB_PACKAGES
    }

    /** Publishes to the user's local ~/.m2/repository for testing in other local projects. */
    fun mavenLocal() {
        _enabled += PublishTarget.MAVEN_LOCAL
    }
}
