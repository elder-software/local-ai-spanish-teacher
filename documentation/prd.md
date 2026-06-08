# Product Requirement Document (PRD): Anytime Spanish AI

## 1. System Context & Tech Stack

This PRD is optimized for step-by-step execution inside Cursor AI. The architecture relies on Android's native ecosystem and system-level AI compilation.

* **Language & UI:** Kotlin, Jetpack Compose, Material 3 (Strict semantic token usage: `MaterialTheme.colorScheme.primary`, `MaterialTheme.typography.bodyLarge`, etc. No hardcoded hex colors).
* **AI Engine:** Google Gemma 4 (E2B) via the Android AICore / ML Kit GenAI Prompt API.
* **Audio Pipelines:** Android `AudioRecord` (PCM 16-bit, 16kHz) and native `TextToSpeech` (Locale: `es-ES` / `es-MX`).
* **Architecture:** Clean Architecture / MVVM. UI state must be driven by a single uni-directional state flow (`StateFlow`).

---

## 2. Core App Flow & Minimal Screen Archetype

To minimize overhead for the MVP, the app consists of exactly two screens managed via Compose Navigation.

### 2.1 Screen 1: Dashboard & Topic Selection (`DashboardScreen.kt`)

* **Functional Goal:** Let the user pick a conversational vector and verify that the offline model is initialized.
* **UI Components:**
* Header displaying app state or connection readiness.
* A grid or list of `OutlinedCard` components representing predefined topics (e.g., "Ordering Food in Madrid", "Job Interview Practice").
* A status indicator tracking the model download/readiness state via Android AICore.



### 2.2 Screen 2: Active Chat & Session Review (`ChatScreen.kt`)

* **Functional Goal:** Live voice conversation tracking, state switches, parsing engine execution, and post-session performance feedback display.
* **State 1: Active Chat**
* An animated `FilledTonalButton` or `IconButton` using a microphone icon to act as a Hold-to-Talk or Tap-to-Talk toggle.
* A clean scrolling conversation history rendering text output once processed.


* **State 2: Summary/Review**
* Triggered when clicking an "End Chat" action item in the TopAppBar.
* An `ElevatedCard` parsing and displaying the hidden reasoning track (`<|think|>`) evaluating grammar, vocabulary choices, and areas for improvement.



---

## 3. Step-by-Step Implementation Architecture

Follow these sequential steps cleanly. Implement one file/layer at a time, verifying compiler and runtime success before advancing.

### Step 1: Model Types & Stream Parsing Engine

Create the core domain models and text stream processors. We must split the output text into what the AI displays/speaks vs. what it tracks internally for the final evaluation.

```kotlin
// Path: domain/model/ChatModels.kt
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class SessionEvaluation(
    val grammaticalCorrections: List<String>,
    val positiveTakeaways: List<String>,
    val overallScore: String
)

// Path: domain/parser/GemmaStreamParser.kt
class GemmaStreamParser {
    private var isInsideThinkBlock = false
    private val thoughtBuffer = StringBuilder()

    /**
     * Parses streaming tokens sequentially.
     * Extracts text inside <|think|> tags without leaking them to the user or TTS.
     */
    fun processToken(token: String, onUserVisibleText: (String) -> Unit): String? {
        var cleanToken = token
        if (cleanToken.contains("<|think|>")) {
            isInsideThinkBlock = true
            cleanToken = cleanToken.substringBefore("<|think|>")
        }
        
        if (isInsideThinkBlock) {
            if (cleanToken.contains("</|think|>")) {
                isInsideThinkBlock = false
                thoughtBuffer.append(cleanToken.substringBefore("</|think|>"))
                val completedThought = thoughtBuffer.toString()
                thoughtBuffer.setLength(0) 
                onUserVisibleText(cleanToken.substringAfter("</|think|>"))
                return completedThought
            } else {
                thoughtBuffer.append(cleanToken)
                return null
            }
        } else {
            onUserVisibleText(cleanToken)
            return null
        }
    }
}

```

### Step 2: Android AICore Integration Service

Establish communication with the system GenAI service layer.

*Cursor Instruction: Ensure you add `implementation("com.google.mlkit:genai-prompt:1.0.0-beta2")` to the `build.gradle.kts` file.*

```kotlin
// Path: data/repository/AiCoreRepository.kt
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.content
import com.google.mlkit.genai.prompt.FeatureStatus
import com.google.mlkit.genai.prompt.DownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AiCoreRepository {
    private val generativeModel = Generation.getClient()

    fun checkModelAvailability(): Flow<Int> = flow {
        emit(generativeModel.checkStatus())
    }

    fun downloadModel(): Flow<DownloadStatus> = flow {
        generativeModel.download().collect { status ->
            emit(status)
        }
    }

    fun generateStreamingResponse(systemPrompt: String, audioData: ByteArray): Flow<String> = flow {
        val payload = content {
            text(systemPrompt)
            blob("audio/wav", audioData)
        }
        generativeModel.generateContentStream(payload).collect { chunk ->
            chunk.text?.let { emit(it) }
        }
    }
}

```

### Step 3: State Management & ViewModel Design

The architecture requires a predictable screen state flow to support conversational mutations.

```kotlin
// Path: ui/chat/ChatUiState.kt
sealed interface ChatUiState {
    object Initializing : ChatUiState
    
    data class ActiveConversation(
        val messages: List<ChatMessage> = emptyList(),
        val isRecording: Boolean = false,
        val currentTopic: String
    ) : ChatUiState
    
    data class EvaluationSummary(
        val transcript: List<ChatMessage>,
        val evaluation: SessionEvaluation
    ) : ChatUiState
}

```

*Cursor Instruction: Develop the corresponding `ChatViewModel` to expose a `StateFlow<ChatUiState>` reacting to intentional user intents: `SelectTopic`, `ToggleRecording`, `EndConversation`.*

### Step 4: Material 3 UI Layout Implementation

Construct UI layers using semantic typography and theme shapes to support modular styling overrides.

* **Backgrounds:** Use `Surface(color = MaterialTheme.colorScheme.background)` as root components.
* **Cards:** Use `ElevatedCard` for evaluations and `OutlinedCard` for selecting conversation setups. Set shape configurations to `MaterialTheme.shapes.medium`.
* **Chat Bubbles:** Build user vs. AI text blocks explicitly using tinted variations:
* User: `Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large)`
* AI Partner: `Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large)`



---

## 4. Operational Requirements & Quality Checkpoints

When executing this code via Cursor, verify the following constraints:

1. **Safety & Evictions:** The `AiCoreRepository` must handle hardware model evictions and thermal exceptions safely by catching platform cancellation exceptions without killing the application process scope.
2. **UI Theming:** Audit the layout files (`DashboardScreen` and `ChatScreen`). Ensure no hardcoded color hex values, text style sizes, or corner radii exist. Everything must inherit directly from the Material 3 `MaterialTheme` context.