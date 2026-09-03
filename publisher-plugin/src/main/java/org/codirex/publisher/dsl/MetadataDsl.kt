package org.codirex.publisher.dsl

import org.codirex.publisher.metadata.GithubRepoRef
import org.codirex.publisher.metadata.GithubSyncEngine
import org.codirex.publisher.metadata.RepoMetadata
import org.gradle.api.Project
import org.json.JSONObject
import java.io.File

/**
 * `metadata { syncFrom(github("codirex/axiom")); license = Licenses.APACHE_2_0 }`
 *
 * [syncFrom] just records which GitHub repo to pull from; the actual network
 * call happens lazily the first time [resolve] is called (during POM
 * generation), so plain `./gradlew tasks` or offline builds that never touch
 * publishing never hit the network.
 *
 * Anything set explicitly here - directly, via [developer], or via
 * [loadOverridesFrom] - wins over whatever GitHub sync would have filled in,
 * because [resolve] only fills in fields that are still blank/empty.
 */
class MetadataDsl(private val project: Project) {

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
        val json = JSONObject(file.readText())

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
