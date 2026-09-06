package org.codirex.publisher.targets.central

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packages a local Maven staging repository into the `bundle.zip` layout
 * required by the Sonatype Central Publisher Portal.
 *
 * Holds no state at all (no `Project`, nothing) - pure `File` in, `File`
 * out, so it's directly unit-testable and trivially Configuration-Cache-safe
 * if ever referenced from task state.
 */
object BundleGenerator {

    private val CHECKSUM_EXTENSIONS = setOf("md5", "sha1", "sha256", "sha512", "asc")
    private const val EXCLUDED_FILE_NAME = "maven-metadata-local.xml"

    fun createBundle(stagingRepoDir: File, bundleFile: File): File {
        require(stagingRepoDir.exists()) {
            "Staging repository not found at ${stagingRepoDir.absolutePath}. Run " +
                "the corresponding 'publish...Repository' task first."
        }

        generateMissingChecksums(stagingRepoDir)

        bundleFile.parentFile?.mkdirs()
        if (bundleFile.exists()) bundleFile.delete()

        ZipOutputStream(bundleFile.outputStream().buffered()).use { zip ->
            stagingRepoDir.walkTopDown()
                .filter { it.isFile && it.name != EXCLUDED_FILE_NAME }
                .sortedBy { it.relativeTo(stagingRepoDir).path } // deterministic bundle contents
                .forEach { file ->
                    val entryName = stagingRepoDir.toPath().relativize(file.toPath()).toString()
                    zip.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        return bundleFile
    }

    /**
     * Gradle's `maven-publish` does not generate `.md5`/`.sha1` sidecar
     * files when publishing to a local (`file://`) repository - a known,
     * long-standing limitation (gradle/gradle#22482) - but Central's bundle
     * validation expects them for every artifact. Fill in whatever's
     * missing before zipping, so this doesn't have to be a manual step
     * every release (as it was before this fix).
     */
    private fun generateMissingChecksums(stagingRepoDir: File) {
        stagingRepoDir.walkTopDown()
            .filter { file ->
                file.isFile && file.extension.lowercase() !in CHECKSUM_EXTENSIONS && file.name != EXCLUDED_FILE_NAME
            }
            .forEach { file ->
                writeChecksumIfMissing(file, "md5", "MD5")
                writeChecksumIfMissing(file, "sha1", "SHA-1")
            }
    }

    private fun writeChecksumIfMissing(file: File, extension: String, algorithm: String) {
        val checksumFile = File(file.parentFile, "${file.name}.$extension")
        if (checksumFile.exists()) return
        checksumFile.writeText(checksumOf(file, algorithm))
    }

    private fun checksumOf(file: File, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
