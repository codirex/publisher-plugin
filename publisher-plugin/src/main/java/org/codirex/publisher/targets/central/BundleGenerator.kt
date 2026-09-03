package org.codirex.publisher.targets.central

import org.gradle.api.Project
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packages a local Maven staging repository into the `bundle.zip` layout
 * required by the Sonatype Central Publisher Portal.
 */
class BundleGenerator(private val project: Project) {

    fun createBundle(stagingRepoDir: File): File {
        require(stagingRepoDir.exists()) {
            "Staging repository not found at ${stagingRepoDir.absolutePath}. Run " +
                "'publishReleasePublicationToCentralStagingRepository' first."
        }

        val bundleFile = File(project.layout.buildDirectory.get().asFile, "publisher/bundle.zip")
        bundleFile.parentFile.mkdirs()
        if (bundleFile.exists()) bundleFile.delete()

        ZipOutputStream(bundleFile.outputStream().buffered()).use { zip ->
            stagingRepoDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val entryName = stagingRepoDir.toPath().relativize(file.toPath()).toString()
                zip.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return bundleFile
    }
}
