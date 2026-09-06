package org.codirex.publisher.dsl

enum class PublishTarget { MAVEN_CENTRAL, GITHUB_PACKAGES, MAVEN_LOCAL }

/** `targets { mavenCentral(); githubPackages(); mavenLocal() }` */
class TargetsDsl {
    private val _enabled = linkedSetOf<PublishTarget>()
    val enabled: Set<PublishTarget> get() = _enabled

    /** Central Portal's own two-stage options: automatic publish after validation, or manual review first. */
    var centralAutoPublish: Boolean = true
        private set

    /** Explicit `owner/repo` override for GitHub Packages, if you don't want it inferred from `metadata { syncFrom(...) }`. */
    var githubRepoOverride: String? = null
        private set

    /**
     * @param autoPublish if true (default), Central publishes automatically once validation
     *   passes. If false, the deployment is uploaded and validated but left in `PENDING`
     *   for manual review/publish at central.sonatype.com/publishing/deployments.
     */
    fun mavenCentral(autoPublish: Boolean = true) {
        _enabled += PublishTarget.MAVEN_CENTRAL
        centralAutoPublish = autoPublish
    }

    /**
     * @param repo optional explicit `"owner/repo"` - if omitted, inferred from
     *   `metadata { syncFrom(github("owner/repo")) }`.
     */
    fun githubPackages(repo: String? = null) {
        _enabled += PublishTarget.GITHUB_PACKAGES
        githubRepoOverride = repo
    }

    /** Publishes to the user's local ~/.m2/repository for testing in other local projects. */
    fun mavenLocal() {
        _enabled += PublishTarget.MAVEN_LOCAL
    }
}
