---
name: Add Vosk speech-to-text engine
overview: ""
todos: []
isProject: false
---

---
todos:
  - id: "pr1-vosk-deps-unzip"
    content: "PR 1: Add Vosk dependency, engine allowlist, and unzip helper"
    status: pending
  - id: "pr2-vosk-manager"
    content: "PR 2: VoskSpeechToTextManager + AppContainer wiring"
    status: pending
isProject: false
---
# Add Vosk speech-to-text engine

Add a third `SpeechToTextEngine` implementation backed by Vosk (offline, on-device) so it can be selected via `local.properties` and compared against the existing Moonshine and Android engines. 2 PRs. Constraints: reuse the existing `AudioRecorder` (batch transcription, no Vosk `SpeechService`), reuse OkHttp for model download, use `org.json` for result parsing, keep engine selection in the existing `BuildConfig.SPEECH_TO_TEXT_ENGINE` mechanism.

---

## PR 1: Add Vosk dependency, engine allowlist, and unzip helper

Adds the Vosk Gradle dependency, permits `"vosk"` as an engine value, and adds a standalone zip-extraction utility the manager will use. Independent; can be merged on its own (everything here compiles and the new value falls through to the existing default until PR 2 lands).

**Files (3):**
- `gradle/libs.versions.toml` - add Vosk version + library entry
- `app/build.gradle.kts` - reference the library; widen the engine allowlist
- `app/src/main/java/com/example/localllmvoice/data/audio/ZipExtractor.kt` - new unzip helper

**libs.versions.toml changes (~2 lines):**
- Under `[versions]` add: `vosk = "0.3.75"`
- Under `[libraries]` add:

```toml
vosk-android = { group = "com.alphacephei", name = "vosk-android", version.ref = "vosk" }
```

- Do NOT add a separate JNA entry; `vosk-android` pulls `net.java.dev.jna:jna` transitively.

**app/build.gradle.kts changes (~2 lines):**
- In `dependencies { }` add: `implementation(libs.vosk.android)`
- Change the engine allowlist `takeIf` to also accept `"vosk"`:

```kotlin
val speechToTextEngine = localProperties.getProperty("speechToText.engine", "moonshine")
    .trim()
    .lowercase()
    .takeIf { it == "moonshine" || it == "android" || it == "vosk" }
    ?: "moonshine"
```

- Do NOT change `minSdk`/`compileSdk` or add `packagingOptions` yet. Only add a `packaging { jniLibs.useLegacyPackaging = false }` block if a duplicate-`.so` build error actually appears (see Risks).

**ZipExtractor changes (~45 lines):**
- New file, package `com.example.localllmvoice.data.audio`.
- Single object `ZipExtractor` with one function:

```kotlin
object ZipExtractor {
    /**
     * Extracts [zip] into [targetDir], creating subdirectories as needed.
     * Guards against Zip Slip by rejecting entries that resolve outside [targetDir].
     */
    fun unzip(zip: File, targetDir: File)
}
```

- Implementation: open `ZipInputStream(FileInputStream(zip).buffered())`, iterate `nextEntry`, for each entry resolve `File(targetDir, entry.name)`, verify `canonicalPath` starts with `targetDir.canonicalPath` (throw `IllegalStateException` if not), `mkdirs()` for directory entries or parent dirs, then copy bytes with a 64 KB buffer to a `FileOutputStream`. Close each entry.
- Do NOT pull in any third-party zip library; use `java.util.zip`.

**Acceptance criteria:**
- [ ] Project syncs and builds with `vosk-android` on the classpath.
- [ ] Setting `speechToText.engine=vosk` in `local.properties` produces `BuildConfig.SPEECH_TO_TEXT_ENGINE == "vosk"` (until PR 2, `AppContainer` falls through to the Moonshine default).
- [ ] `ZipExtractor.unzip` extracts a multi-folder zip and throws on a malicious `../` entry.

**Tests (if applicable):**
- `app/src/test/java/com/example/localllmvoice/data/audio/ZipExtractorTest.kt`: build a small in-memory/temp zip with a nested folder, unzip to a temp dir, assert files exist with correct contents; add a case with an entry name `../evil.txt` and assert it throws.

**Estimated total: ~50 lines, 3 files (+~40 lines test)**

---

## PR 2: VoskSpeechToTextManager + AppContainer wiring

Adds the Vosk engine implementation and selects it when configured. Depends on PR 1 being merged first (needs `libs.vosk.android` and `ZipExtractor`).

**Files (2):**
- `app/src/main/java/com/example/localllmvoice/data/audio/VoskSpeechToTextManager.kt` - new engine
- `app/src/main/java/com/example/localllmvoice/di/AppContainer.kt` - select it

**VoskSpeechToTextManager changes (~170 lines):**
- New file, package `com.example.localllmvoice.data.audio`.
- Class signature mirrors the Moonshine manager:

```kotlin
class VoskSpeechToTextManager(private val context: Context) : SpeechToTextEngine
```

- Fields:
  - `private val audioRecorder = AudioRecorder(context)`
  - `private val client = OkHttpClient.Builder()...` (30s connect/read timeouts, same as `SpeechToTextManager`)
  - `private var model: org.vosk.Model? = null`
  - `private var onStopRequested: (() -> Unit)? = null`
  - `private val modelDir = File(context.filesDir, "vosk/small-es")` (the unpacked model root, i.e. the folder that directly contains `am/`, `conf/`, etc.)
  - Constants in a `companion object`: `TAG`, `MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"`, `MODEL_ZIP_NAME = "vosk-model-small-es-0.42"` (the top-level folder name inside the zip).
- `hasRecordPermission()` -> `audioRecorder.hasRecordPermission()`.
- `isAvailable()` -> `true`.
- `transcribe(languageTag)`: `callbackFlow { ... }` following the Moonshine structure exactly:
  1. Permission check -> `SttEvent.Failure` + close if missing.
  2. If `!isModelReady()`: download + unzip, emitting `SttEvent.Partial("Downloading Spanish STT model (n%)…")`; on failure emit `SttEvent.Failure` and close.
  3. `ensureModelLoaded()` (build `Model(modelDir.absolutePath)` once); on failure emit `SttEvent.Failure` and close.
  4. Set `onStopRequested = { launch { ... } }` that: emits `Partial("Transcribing…")`, calls `audioRecorder.stopAndGetRawPcm()`, handles failure/empty, runs `performTranscription(pcm)`, emits `SttEvent.Final(text)`, and `close()` in a `finally`.
  5. `audioRecorder.start()`; on failure emit `Failure` + close.
  6. Emit `Partial("Listening…")`.
  7. `awaitClose { onStopRequested = null; audioRecorder.cancel() }`.
- `stopListening()` -> `onStopRequested?.invoke()`.
- `isModelReady()`: `File(modelDir, "am").isDirectory && File(modelDir, "conf").isDirectory` (presence of the model's standard subfolders).
- `downloadModel(onProgress)`: `withContext(Dispatchers.IO)`. Download `MODEL_URL` to a temp `File(context.cacheDir, "vosk-model.zip")` using the same OkHttp streaming + percent-progress loop as `SpeechToTextManager.downloadModel`. Then `ZipExtractor.unzip(zip, context.filesDir.resolve("vosk"))`, then move/rename the extracted `vosk/<MODEL_ZIP_NAME>` folder to `modelDir` (`renameTo`), then delete the temp zip. Emit `onProgress(100)` on the main dispatcher.
  - Do NOT assume the zip extracts directly into `modelDir`; it contains a single top-level `vosk-model-small-es-0.42/` folder that must be relocated to `modelDir`.
- `ensureModelLoaded()`: if `model != null` return; else `model = Model(modelDir.absolutePath)`; log success. (`org.vosk.Model`.)
- `performTranscription(pcmBytes: ByteArray): String`: `withContext(Dispatchers.Default)`:
  - `val recognizer = Recognizer(model, AudioRecorder.SAMPLE_RATE.toFloat())`
  - Feed the buffer in chunks (e.g. 4096-byte windows) via `recognizer.acceptWaveForm(chunk, chunk.size)` to avoid one huge call.
  - After the loop, read `recognizer.finalResult` (JSON string).
  - Parse with `org.json.JSONObject(finalJson).optString("text").trim()`.
  - `recognizer.close()` in a `finally`.
  - Return the text.
- Do NOT use `org.vosk.android.SpeechService` or `org.vosk.android.RecognitionListener`; this manager must not open its own `AudioRecord`.
- Do NOT add a JSON dependency; use `org.json` (bundled with Android).

**AppContainer changes (~2 lines):**
- Add import for `VoskSpeechToTextManager`.
- Add a branch to `createSpeechToTextManager()`:

```kotlin
private fun createSpeechToTextManager(): SpeechToTextEngine =
    when (BuildConfig.SPEECH_TO_TEXT_ENGINE) {
        "android" -> AndroidSpeechToTextManager(appContext)
        "vosk" -> VoskSpeechToTextManager(appContext)
        "moonshine" -> SpeechToTextManager(appContext)
        else -> SpeechToTextManager(appContext)
    }
```

**Acceptance criteria:**
- [ ] With `speechToText.engine=vosk`, first recording downloads + unzips the model (progress shown as `Partial`), then transcribes Spanish speech on-device and emits a non-empty `SttEvent.Final`.
- [ ] Second recording skips the download (model already on disk) and transcribes.
- [ ] Revoked mic permission yields `SttEvent.Failure("Microphone permission not granted")`.
- [ ] No `AudioRecord` conflict: the chat record/stop flow behaves the same as with the Moonshine engine.

**Tests (if applicable):**
- None required (engine integration is I/O- and native-bound). The `ZipExtractor` logic is covered in PR 1. If desired, a small unit test asserting `org.json` parsing of `{"text":"hola"}` -> `"hola"`, but this is optional.

**Estimated total: ~175 lines, 2 files**

---

## Risks / notes
- **Native packaging:** `vosk-android` ships `.so` libs and JNA. If a build error about duplicate native libs appears, add to `android { }`:

```kotlin
packaging { jniLibs.useLegacyPackaging = false }
```

  Only add this if the error actually occurs; don't pre-emptively change packaging.
- **Model size:** ~39 MB download on first use of the Vosk engine; this is by design (matches the Moonshine download-on-demand approach) and keeps the APK small.
- **Switching engines requires editing `local.properties` and rebuilding**, since selection is compile-time via `BuildConfig`. That's the existing mechanism; no runtime toggle is in scope.
