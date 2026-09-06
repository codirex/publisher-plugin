package org.codirex.publisher.dsl

import org.codirex.publisher.credentials.CredentialsDsl
import org.gradle.api.Project
import javax.inject.Inject

/**
 * The root-level `publisherEcosystem { ... }` block for multi-module setups.
 * `autoPublishModules { }` applies the plugin to every subproject and seeds
 * each one's [PublisherExtension] with the shared group id/license/credential
 * strategy before running the per-module configuration the caller supplied.
 */
open class EcosystemExtension @Inject constructor(private val project: Project) {

    var baseGroupId: String = ""
    var sharedLicense: License = Licenses.APACHE_2_0
    val credentials: CredentialsDsl = CredentialsDsl()

    fun sharedLicense(license: License) {
        sharedLicense = license
    }

    fun autoPublishModules(configure: PublisherExtension.(Project) -> Unit) {
        project.subprojects { module ->
            module.pluginManager.apply("org.codirex.publisher")
            module.extensions.configure(PublisherExtension::class.java) { ext ->
                val moduleVersion = module.version.toString()
                if (baseGroupId.isNotBlank() && moduleVersion.isNotBlank() && moduleVersion != "unspecified" && ext.identityRef == null) {
                    ext.identity(baseGroupId, module.name, moduleVersion)
                }
                ext.metadata.license = sharedLicense
                if (credentials.autoDetectEnabled) {
                    ext.credentials.autoDetect()
                }
                ext.configure(module)
            }
        }
    }
}
