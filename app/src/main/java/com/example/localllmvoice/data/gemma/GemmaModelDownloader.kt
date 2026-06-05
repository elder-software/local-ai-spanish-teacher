package com.example.localllmvoice.data.gemma

import android.util.Log
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request

data class ModelDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
)

class GemmaModelDownloader(
    private val modelStore: GemmaModelStore,
    private val okHttpClient: OkHttpClient = OkHttpClient(),
) {
    // Blocking network/IO runs upstream on Dispatchers.IO via flowOn, so emit() stays in the
    // collector's context and the flow context-preservation invariant is honoured.
    fun download(): Flow<ModelDownloadProgress> = flow {
        modelStore.ensureModelDirectory()
        val target = modelStore.modelFile
        val temp = modelStore.tempDownloadFile
        var existing = if (temp.isFile) temp.length() else 0L

        val requestBuilder = Request.Builder().url(GemmaModelConfig.MODEL_URL)
        if (com.example.localllmvoice.BuildConfig.HUGGING_FACE_TOKEN.isNotEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer ${com.example.localllmvoice.BuildConfig.HUGGING_FACE_TOKEN}")
        }
        if (existing > 0) {
            requestBuilder.addHeader("Range", "bytes=$existing-")
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) {
                throw IllegalStateException("Download failed: HTTP ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("Empty response body")
            val contentLength = body.contentLength()
            val totalBytes = when {
                response.code == 206 -> existing + contentLength
                contentLength > 0 -> contentLength
                else -> GemmaModelConfig.ESTIMATED_SIZE_BYTES
            }

            RandomAccessFile(temp, "rw").use { output ->
                if (existing > 0) output.seek(existing)
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        existing += read
                        emit(ModelDownloadProgress(existing, totalBytes))
                    }
                }
            }

            if (target.exists()) {
                target.delete()
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }

            if (!modelStore.isModelReady()) {
                target.delete()
                temp.delete()
                throw IllegalStateException(
                    "Download finished but model file is invalid. Check storage and try again.",
                )
            }

            Log.i(TAG, "${GemmaModelConfig.MODEL_LABEL} model saved at ${target.canonicalPath}")
            emit(ModelDownloadProgress(existing, totalBytes))
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val TAG = "GemmaModelDownloader"
    }
}
