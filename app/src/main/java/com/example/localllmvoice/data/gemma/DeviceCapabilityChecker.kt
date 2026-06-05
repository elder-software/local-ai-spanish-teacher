package com.example.localllmvoice.data.gemma

import android.app.ActivityManager
import android.content.Context
import android.os.Build

data class DeviceCapability(
    val deviceSummary: String,
    val totalRamBytes: Long,
    val freeStorageBytes: Long,
    val meetsRamRequirement: Boolean,
    val meetsStorageRequirement: Boolean,
    val isEmulator: Boolean,
) {
    val canRunConfiguredModel: Boolean =
        meetsRamRequirement && meetsStorageRequirement

    val hints: List<String>
        get() = buildList {
            if (isEmulator) {
                add("Emulators are slow for on-device LLMs; a physical device with a GPU is strongly recommended.")
            }
            if (!meetsRamRequirement) {
                add(
                    "${GemmaModelConfig.MODEL_LABEL} needs about " +
                        "${GemmaModelConfig.MIN_TOTAL_RAM_BYTES / 1_000_000_000} GB RAM. " +
                        "This device reports ${totalRamBytes / 1_000_000_000} GB.",
                )
            }
            if (!meetsStorageRequirement) {
                add(
                    "Need ~${requiredStorageGb()} GB free storage " +
                        "for the model download (${freeStorageBytes / 1_000_000_000} GB available).",
                )
            }
            if (canRunConfiguredModel) {
                add("Download ${GemmaModelConfig.MODEL_LABEL} once on Wi‑Fi, then chat fully offline.")
            }
        }

    private fun requiredStorageGb(): Long =
        (GemmaModelConfig.MIN_FREE_STORAGE_BYTES + 999_999_999L) / 1_000_000_000L
}

class DeviceCapabilityChecker(
    private val context: Context,
    private val modelStore: GemmaModelStore,
) {
    fun evaluate(): DeviceCapability {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalRam = memoryInfo.totalMem
        val freeStorage = modelStore.freeStorageBytes()

        return DeviceCapability(
            deviceSummary = "${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})",
            totalRamBytes = totalRam,
            freeStorageBytes = freeStorage,
            meetsRamRequirement = totalRam >= GemmaModelConfig.MIN_TOTAL_RAM_BYTES,
            meetsStorageRequirement = freeStorage >= GemmaModelConfig.MIN_FREE_STORAGE_BYTES,
            isEmulator = isProbablyEmulator(),
        )
    }

    private fun isProbablyEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT
        return fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
            Build.HARDWARE.contains("ranchu", ignoreCase = true)
    }
}
