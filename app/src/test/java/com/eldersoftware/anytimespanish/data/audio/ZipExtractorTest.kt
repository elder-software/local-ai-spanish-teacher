package com.eldersoftware.anytimespanish.data.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipExtractorTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun unzip_extractsNestedFoldersWithCorrectContents() {
        val zipFile = tempFolder.newFile("test.zip")
        val targetDir = tempFolder.newFolder("output")

        writeZip(
            zipFile,
            mapOf(
                "nested/hello.txt" to "hello world",
                "nested/deep/data.bin" to byteArrayOf(1, 2, 3),
            ),
        )

        ZipExtractor.unzip(zipFile, targetDir)

        val hello = File(targetDir, "nested/hello.txt")
        val data = File(targetDir, "nested/deep/data.bin")
        assertTrue(hello.isFile)
        assertTrue(data.isFile)
        assertEquals("hello world", hello.readText())
        assertArrayEquals(byteArrayOf(1, 2, 3), data.readBytes())
    }

    @Test(expected = IllegalStateException::class)
    fun unzip_rejectsZipSlipEntry() {
        val zipFile = tempFolder.newFile("evil.zip")
        val targetDir = tempFolder.newFolder("safe")

        writeZip(zipFile, mapOf("../evil.txt" to "malicious"))

        ZipExtractor.unzip(zipFile, targetDir)
    }

    private fun writeZip(zipFile: File, entries: Map<String, Any>) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            entries.forEach { (name, content) ->
                zipOut.putNextEntry(ZipEntry(name))
                when (content) {
                    is String -> zipOut.write(content.toByteArray())
                    is ByteArray -> zipOut.write(content)
                }
                zipOut.closeEntry()
            }
        }
    }
}
