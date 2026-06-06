package com.example.localllmvoice.data.audio

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object ZipExtractor {
    private const val BUFFER_SIZE = 64 * 1024

    /**
     * Extracts [zip] into [targetDir], creating subdirectories as needed.
     * Guards against Zip Slip by rejecting entries that resolve outside [targetDir].
     */
    fun unzip(zip: File, targetDir: File) {
        val targetCanonical = targetDir.canonicalPath
        targetDir.mkdirs()

        ZipInputStream(FileInputStream(zip).buffered()).use { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null) {
                val outputFile = File(targetDir, entry.name)
                val outputCanonical = outputFile.canonicalPath
                if (!outputCanonical.startsWith(targetCanonical + File.separator) &&
                    outputCanonical != targetCanonical
                ) {
                    throw IllegalStateException("Zip entry outside target dir: ${entry.name}")
                }

                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var read = zipInput.read(buffer)
                        while (read != -1) {
                            output.write(buffer, 0, read)
                            read = zipInput.read(buffer)
                        }
                    }
                }

                zipInput.closeEntry()
                entry = zipInput.nextEntry
            }
        }
    }
}
