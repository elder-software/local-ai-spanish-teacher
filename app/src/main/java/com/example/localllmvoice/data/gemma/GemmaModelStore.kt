package com.example.localllmvoice.data.gemma

import android.content.Context
import android.os.StatFs
import java.io.File

/**
 * Resolves on-disk paths for the configured on-device model artifact.
 * LiteRT-LM requires a path to the `.litertlm` file — not the parent directory.
 */
class GemmaModelStore(context: Context) {
    private val appFilesDir: File = context.applicationContext.filesDir

    val modelDirectory: File
        get() {
            val dir = File(appFilesDir, MODELS_SUBDIR)
            if (dir.exists() && !dir.isDirectory) {
                dir.delete()
            }
            dir.mkdirs()
            return dir
        }

    val modelFile: File
        get() = File(modelDirectory, GemmaModelConfig.MODEL_FILE_NAME)

    val tempDownloadFile: File
        get() = File(modelDirectory, "${GemmaModelConfig.MODEL_FILE_NAME}.tmp")

    fun ensureModelDirectory(): File {
        val dir = modelDirectory
        purgeStaleModels(dir)
        return dir
    }

    /** Removes model artifacts left over from a previously configured model to reclaim storage. */
    private fun purgeStaleModels(dir: File) {
        val keep = setOf(
            GemmaModelConfig.MODEL_FILE_NAME,
            "${GemmaModelConfig.MODEL_FILE_NAME}.tmp",
        )
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in keep && file.name.endsWith(".litertlm")) {
                runCatching { file.delete() }
            }
            if (file.isFile && file.name.endsWith(".litertlm.tmp") && file.name !in keep) {
                runCatching { file.delete() }
            }
        }
    }

    fun freeStorageBytes(): Long {
        return try {
            val dir = ensureModelDirectory()
            val stat = StatFs(dir.absolutePath)
            stat.availableBytes
        } catch (e: IllegalArgumentException) {
            try {
                val stat = StatFs(android.os.Environment.getDataDirectory().absolutePath)
                stat.availableBytes
            } catch (e2: Exception) {
                0L
            }
        }
    }

    fun isModelReady(): Boolean {
        val file = modelFile
        return file.isFile && file.length() >= GemmaModelConfig.MIN_MODEL_FILE_BYTES
    }

    /**
     * Absolute path to the `.litertlm` file for [com.google.ai.edge.litertlm.EngineConfig].
     */
    fun resolveEngineModelPath(): Result<String> {
        ensureModelDirectory()
        val file = modelFile
        return when {
            file.isDirectory -> Result.failure(
                IllegalStateException(
                    "Expected a model file but found a directory at ${file.absolutePath}. " +
                        "Delete the models folder and download again.",
                ),
            )
            !file.isFile -> Result.failure(
                IllegalStateException(
                    "${GemmaModelConfig.MODEL_LABEL} model not found. Download it from the dashboard first.",
                ),
            )
            file.length() < GemmaModelConfig.MIN_MODEL_FILE_BYTES -> Result.failure(
                IllegalStateException(
                    "Model file looks incomplete (${file.length()} bytes). Delete it and re-download.",
                ),
            )
            else -> Result.success(file.canonicalPath)
        }
    }

    companion object {
        private const val MODELS_SUBDIR = "models"
    }
}
