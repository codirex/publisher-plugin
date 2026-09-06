package org.codirex.publisher.dsl

import org.codirex.publisher.metadata.GithubRepoRef
import org.codirex.publisher.metadata.GithubSyncEngine
import org.codirex.publisher.metadata.RepoMetadata
import org.json.JSONObject
import java.io.File

/**
 * `metadata { syncFrom(github("codirex/axiom")); license = Licenses.APACHE_2_0 }`
 *
 * [syncFrom] just records which GitHub repo to pull from; the actual network
 * call happens lazily the first time [resolve] is called. [PomGenerator]
 * wires that call behind a `Provider`, so it fires when the POM is actually
 * *written* (i.e. a publish task executes), not during every configuration
 * phase - plain `./gradlew tasks` or offline builds that never touch
 * publishing never hit the network.
 *
 * Deliberately holds no [org.gradle.api.Project] reference: instances of
 * this class get captured inside `Provider` lambdas that become inputs of
 * Gradle's own `GenerateMavenPom` task, and Configuration Cache cannot
 * serialize anything that transitively holds a live `Project`.
 *
 * Anything set explicitly here - directly, via [developer], or via
 * [loadOverridesFrom] - wins over whatever GitHub sync would have filled in,
 * because [resolve] only fills in fields that are still blank/empty.
 */
class MetadataDsl {

    var license: License = Licenses.APACHE_2_0
    var description: String = ""
    var projectUrl: String = ""
    var scmUrl: String = ""

    private val _developers = mutableListOf<Developer>()
    val developers: List<Developer> get() = _developers

    var repoRef: GithubRepoRef? = null
        private set

    private var synced: RepoMetadata? = null

    fun syncFrom(repo: GithubRepoRef) {
        repoRef = repo
    }

    /** Adds one developer/contributor entry to the POM. */
    fun developer(id: String, name: String, email: String? = null, url: String? = null, roles: List<String> = emptyList()) {
        _developers += Developer(id, name, email, url, roles)
    }

    /** Bulk alternative to calling [developer] repeatedly, e.g. when building the list programmatically. */
    fun developers(vararg developers: Developer) {
        _developers += developers
    }

    /**
     * Best-effort, network-free check for whether the generated POM is
     * likely to have real content in `description`/`url` - either an
     * explicit value was set, or a GitHub repo is registered to pull one
     * from. This can't *guarantee* a non-blank POM (a registered repo might
     * itself have no description on GitHub), but it catches the common
     * mistake of configuring neither.
     */
    fun hasDescriptionSource(): Boolean = description.isNotBlank() || repoRef != null
    fun hasUrlSource(): Boolean = projectUrl.isNotBlank() || repoRef != null
    fun hasScmSource(): Boolean = scmUrl.isNotBlank() || repoRef != null

    /**
     * Overrides metadata from a JSON file, for teams that keep this
     * centrally rather than duplicating it in every module's build script:
     * ```json
     * {
     *   "description": "...",
     *   "projectUrl": "...",
     *   "scmUrl": "...",
     *   "license": { "name": "...", "url": "...", "spdxId": "Apache-2.0" },
     *   "developers": [
     *     { "id": "ahmed", "name": "Ahmed", "email": "a@b.com", "url": "...", "roles": ["Owner"] }
     *   ]
     * }
     * ```
     * Every field is optional; only what's present overrides the current value.
     * (XML isn't supported yet - open an issue/PR if you need it.)
     */
    fun loadOverridesFrom(file: File) {
        require(file.exists()) { "Metadata override file not found: ${file.absolutePath}" }
        val json = try {
            JSONObject(file.readText())
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse metadata override file ${file.absolutePath}: ${e.message}", e)
        }

        json.optStringOrNull("description")?.let { description = it }
        json.optStringOrNull("projectUrl")?.let { projectUrl = it }
        json.optStringOrNull("scmUrl")?.let { scmUrl = it }

        json.optJSONObject("license")?.let { licenseJson ->
            license = License(
                name = licenseJson.optStringOrNull("name") ?: license.name,
                url = licenseJson.optStringOrNull("url") ?: license.url,
                spdxId = licenseJson.optStringOrNull("spdxId") ?: license.spdxId
            )
        }

        json.optJSONArray("developers")?.let { array ->
            _developers.clear()
            for (i in 0 until array.length()) {
                val dev = array.getJSONObject(i)
                require(dev.has("id") && dev.has("name")) {
                    "developers[$i] in ${file.name} is missing required \"id\" or \"name\" field."
                }
                _developers += Developer(
                    id = dev.getString("id"),
                    name = dev.getString("name"),
                    email = dev.optStringOrNull("email"),
                    url = dev.optStringOrNull("url"),
                    roles = dev.optJSONArray("roles")?.let { roles ->
                        (0 until roles.length()).map { roles.getString(it) }
                    }.orEmpty()
                )
            }
        }
    }

    /** Fetches (and caches) metadata from GitHub, filling in any blanks the user didn't set explicitly. */
    fun resolve(): RepoMetadata? {
        val ref = repoRef ?: return null
        synced?.let { return it }

        val fetched = GithubSyncEngine().fetch(ref)
        synced = fetched
        if (description.isBlank()) description = fetched.description.orEmpty()
        if (projectUrl.isBlank()) projectUrl = fetched.htmlUrl.orEmpty()
        if (scmUrl.isBlank()) scmUrl = fetched.cloneUrl.orEmpty()
        return fetched
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null
}

fun github(fullName: String): GithubRepoRef {
    val parts = fullName.split("/")
    require(parts.size == 2 && parts.all { it.isNotBlank() }) {
        "Expected \"owner/repo\", got \"$fullName\""
    }
    return GithubRepoRef(owner = parts[0], repo = parts[1])
}
