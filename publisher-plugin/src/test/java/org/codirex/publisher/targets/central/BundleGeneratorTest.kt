package org.codirex.publisher.targets.central

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BundleGeneratorTest {

    @Test
    fun `generates missing checksums, preserves existing ones, and excludes local metadata`() {
        val staging = Files.createTempDirectory("staging").toFile()
        val artifactDir = File(staging, "org/example/lib/1.0.0").apply { mkdirs() }
        File(artifactDir, "lib-1.0.0.jar").writeText("fake-jar-bytes")
        File(artifactDir, "maven-metadata-local.xml").writeText("<metadata/>")
        val existingSha1 = File(artifactDir, "lib-1.0.0.jar.sha1").apply { writeText("PRESET-DO-NOT-OVERWRITE") }

        val bundle = File(Files.createTempDirectory("bundle-out").toFile(), "bundle.zip")
        val result = BundleGenerator.createBundle(staging, bundle)

        assertTrue(File(artifactDir, "lib-1.0.0.jar.md5").exists(), "md5 should be generated for an artifact missing one")
        assertEquals("PRESET-DO-NOT-OVERWRITE", existingSha1.readText(), "an existing checksum must not be overwritten")

        ZipFile(result).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue(names.any { it.endsWith("lib-1.0.0.jar") }, "jar should be bundled")
            assertTrue(names.any { it.endsWith("lib-1.0.0.jar.md5") }, "generated md5 should be bundled")
            assertTrue(names.any { it.endsWith("lib-1.0.0.jar.sha1") }, "generated sha1 should be bundled")
            assertTrue(names.none { it.endsWith("maven-metadata-local.xml") }, "local-only metadata must not be bundled")
        }
    }

    @Test
    fun `fails with a clear message when the staging dir is missing`() {
        val missing = File(Files.createTempDirectory("missing-parent").toFile(), "does-not-exist")
        val bundle = File(Files.createTempDirectory("bundle-out2").toFile(), "bundle.zip")

        val error = assertFailsWith<IllegalArgumentException> {
            BundleGenerator.createBundle(missing, bundle)
        }
        assertTrue(error.message!!.contains("Staging repository not found"))
    }
}
