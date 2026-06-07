---
name: Onboarding Flow
overview: ""
todos: []
isProject: false
---

---
todos:
  - id: "pr1-onboarding-foundation"
    content: "PR 1: Onboarding foundation (persistence, scaffold, nav gating)"
    status: pending
  - id: "pr2-welcome-screen"
    content: "PR 2: Screen 1 - Welcome"
    status: pending
  - id: "pr3-paywall-screen"
    content: "PR 3: Screen 2 - Own it forever"
    status: pending
  - id: "pr4-download-screen"
    content: "PR 4: Screen 3 - Placing the AI on your device"
    status: pending
isProject: false
---
# Onboarding Flow

A 3-screen first-launch onboarding flow built as fully decoupled screens. 4 PRs: one foundation PR (persistence + shared scaffold + nav gating) that all others depend on, then three independent screen PRs that can be built in parallel.

Architecture decisions (locked):
- Screens 1 & 2 are stateless composables with a single `onContinue` callback. No ViewModel.
- Screen 3 has its own `OnboardingDownloadViewModel` that wraps the existing `DownloadAllModelsUseCase`. No shared cross-screen ViewModel.
- Onboarding-complete flag persisted via a `SharedPreferences` wrapper (synchronous read needed for NavHost start destination).
- Screen 2 paywall copy is informational only; no billing/IAP is implemented.
- Screen 3 "Wi-Fi / strong connection" is static copy; no connectivity detection.

---

## PR 1: Onboarding foundation (persistence, scaffold, nav gating)

Establishes everything the three screens plug into: the persisted completion flag, a shared visual scaffold, the nested nav graph, and start-destination gating. Includes compiling stubs for all three screen composables so the graph builds; PRs 2-4 replace the stub bodies.

Independent prerequisite; PRs 2, 3, 4 all depend on this being merged first.

**Files (7):**
- `app/src/main/java/com/example/localllmvoice/data/onboarding/OnboardingPreferences.kt` - new persistence wrapper
- `app/src/main/java/com/example/localllmvoice/di/AppContainer.kt` - expose `onboardingPreferences`
- `app/src/main/java/com/example/localllmvoice/ui/onboarding/OnboardingScaffold.kt` - new shared layout + primary button
- `app/src/main/java/com/example/localllmvoice/ui/onboarding/OnboardingWelcomeScreen.kt` - new stub
- `app/src/main/java/com/example/localllmvoice/ui/onboarding/OnboardingPaywallScreen.kt` - new stub
- `app/src/main/java/com/example/localllmvoice/ui/onboarding/OnboardingDownloadScreen.kt` - new stub
- `app/src/main/java/com/example/localllmvoice/navigation/AppNavigation.kt` - routes, graph, start-destination gating

**OnboardingPreferences changes (~25 lines):**
- Class backed by `context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)`.
- `fun isComplete(): Boolean` reads key `"onboarding_complete"`, default `false`.
- `fun setComplete(complete: Boolean)` writes the key (use `.edit().putBoolean(...).apply()`).

```kotlin
class OnboardingPreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isComplete(): Boolean = prefs.getBoolean(KEY_COMPLETE, false)

    fun setComplete(complete: Boolean) {
        prefs.edit().putBoolean(KEY_COMPLETE, complete).apply()
    }

    private companion object {
        const val PREFS_NAME = "onboarding_prefs"
        const val KEY_COMPLETE = "onboarding_complete"
    }
}
```

**AppContainer changes (~2 lines):**
- Add `val onboardingPreferences = OnboardingPreferences(appContext)`. Do NOT touch the `speechToTextManager.preload()` init block.

**OnboardingScaffold changes (~70 lines):**
- `OnboardingScaffold(heading: String, body: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`.
- Root `Surface` with `color = MaterialTheme.colorScheme.background`, `Modifier.fillMaxSize()`.
- Inner `Column`: `fillMaxSize()`, `padding(horizontal = 24.dp, vertical = 32.dp)`, `horizontalAlignment = Alignment.CenterHorizontally`.
- `Spacer(Modifier.weight(1f))` above the text block to vertically center the heading/body.
- `heading` as `Text` with `style = MaterialTheme.typography.displayMedium`, `textAlign = TextAlign.Center`, `color = MaterialTheme.colorScheme.onBackground`.
- `body` as `Text` with `style = MaterialTheme.typography.bodyLarge`, `textAlign = TextAlign.Center`, `color = MaterialTheme.colorScheme.onSurfaceVariant`, `Modifier.padding(top = 16.dp)`.
- `Spacer(Modifier.weight(1f))` below the text block.
- Then invoke `content()` (the bottom area: CTAs / progress) inside `Column(Modifier.fillMaxWidth())`.
- Also define a reusable primary button in this file:

```kotlin
@Composable
fun OnboardingPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}
```

- Rationale for amber CTA: matches the screenshot and design.md "Soft Amber" action color (`secondaryContainer`).

**Stub screen changes (~12 lines each):**
- `OnboardingWelcomeScreen(onContinue: () -> Unit, modifier: Modifier = Modifier)` -> body: `OnboardingScaffold(heading = "Welcome", body = "") { }`.
- `OnboardingPaywallScreen(onContinue: () -> Unit, modifier: Modifier = Modifier)` -> same stub shape.
- `OnboardingDownloadScreen(onFinished: () -> Unit, modifier: Modifier = Modifier)` -> same stub shape. NOTE: no ViewModel param yet; PR 4 adds it and updates the graph call site.
- Stubs exist only so the graph compiles. Keep `onContinue` / `onFinished` referenced (e.g. assign to an unused `val` is not needed; just leave the params — unused params are fine).

**AppNavigation changes (~40 lines):**
- Add to `Routes`:

```kotlin
const val ONBOARDING_GRAPH = "onboarding"
const val ONBOARDING_WELCOME = "onboarding/welcome"
const val ONBOARDING_PAYWALL = "onboarding/paywall"
const val ONBOARDING_DOWNLOAD = "onboarding/download"
```

- Change `SoloTalkNavHost` `startDestination` from the hardcoded `Routes.DASHBOARD` to:

```kotlin
val startDestination = remember {
    if (appContainer.onboardingPreferences.isComplete()) Routes.DASHBOARD
    else Routes.ONBOARDING_GRAPH
}
```

- Add a `navigation(startDestination = Routes.ONBOARDING_WELCOME, route = Routes.ONBOARDING_GRAPH) { ... }` block (import `androidx.navigation.compose.navigation`) containing 3 `composable` routes:
  - `ONBOARDING_WELCOME` -> `OnboardingWelcomeScreen(onContinue = { navController.navigate(Routes.ONBOARDING_PAYWALL) })`
  - `ONBOARDING_PAYWALL` -> `OnboardingPaywallScreen(onContinue = { navController.navigate(Routes.ONBOARDING_DOWNLOAD) })`
  - `ONBOARDING_DOWNLOAD` -> `OnboardingDownloadScreen(onFinished = { finishOnboarding() })` where `finishOnboarding` does:

```kotlin
appContainer.onboardingPreferences.setComplete(true)
navController.navigate(Routes.DASHBOARD) {
    popUpTo(Routes.ONBOARDING_GRAPH) { inclusive = true }
}
```

- The persistence write lives in the nav layer (the `onFinished` lambda), keeping screens decoupled from `OnboardingPreferences`.
- Leave the existing `dashboard` / `chat` / `feedback` composables untouched.

**Acceptance criteria:**
- [ ] Fresh install launches into `onboarding/welcome`.
- [ ] App with `onboarding_complete = true` launches straight into `dashboard`.
- [ ] Navigating welcome -> paywall -> download stub works via the CTAs.
- [ ] Calling `onFinished` on the download stub sets the flag and lands on dashboard with the onboarding graph popped (back press from dashboard does not return to onboarding).
- [ ] Project compiles.

**Estimated total: ~170 lines, 7 files**

---

## PR 2: Screen 1 - Welcome

Fills in the welcome screen body. Depends on PR 1 being merged first. Independent of PR 3 and PR 4.

**Files (1):**
- `app/src/main/java/com/example/localllmvoice/ui/onboarding/OnboardingWelcomeScreen.kt`

**OnboardingWelcomeScreen changes (~20 lines):**
- Keep signature `OnboardingWelcomeScreen(onContinue: () -> Unit, modifier: Modifier = Modifier)`.
- Body:

```kotlin
OnboardingScaffold(
    heading = "Welcome to Anytime Spanish",
    body = "Your private, always-ready language companion. Practice " +
        "speaking 100% offline. No subscriptions, no cloud servers, " +
        "no judgment.",
    modifier = modifier,
) {
    OnboardingPrimaryButton(
        text = "Start your journey",
        onClick = onContinue,
    )
}
```

**Acceptance criteria:**
- [ ] Heading, body, and "Start your journey" CTA render per copy.
- [ ] Tapping the CTA navigates to the paywall screen.

**Estimated total: ~20 lines, 1 file**

---

## PR 3: Screen 2 - Own it forever

Fills in the paywall/value-prop screen. Informational copy only — no billing. Depends on PR 1. Independent of PR 2 and PR 4.

**Files (1):**
- `app/src/main/java/com/example/localllmvoice/ui/onboarding/OnboardingPaywallScreen.kt`

**OnboardingPaywallScreen changes (~22 lines):**
- Keep signature `OnboardingPaywallScreen(onContinue: () -> Unit, modifier: Modifier = Modifier)`.
- Body:

```kotlin
OnboardingScaffold(
    heading = "Own it forever",
    body = "We hate subscriptions. We've unlocked two real-world travel " +
        "scenarios for you to try completely free. If you love the " +
        "experience, you can unlock the full library later for a single " +
        "one-time payment of \$29.99.",
    modifier = modifier,
) {
    OnboardingPrimaryButton(
        text = "Get my free scenarios",
        onClick = onContinue,
    )
}
```

- Do NOT add any purchase/billing logic. `onContinue` simply advances to the download screen.

**Acceptance criteria:**
- [ ] Heading, body (with `$29.99` rendering correctly), and "Get my free scenarios" CTA render per copy.
- [ ] Tapping the CTA navigates to the download screen.

**Estimated total: ~22 lines, 1 file**

---

## PR 4: Screen 3 - Placing the AI on your device

The only stateful screen. Wraps the existing `DownloadAllModelsUseCase` in a dedicated ViewModel, renders progress, and on completion finishes onboarding. Depends on PR 1. Independent of PR 2 and PR 3.

Do NOT depend on or reuse `DashboardViewModel`. Do NOT duplicate download logic — call `appContainer.downloadAllModelsUseCase()`.

**Files (4):**
- `app/src/main/java/com/example/localllmvoice/ui/onboarding/OnboardingDownloadViewModel.kt` - new
- `app/src/main/java/com/example/localllmvoice/navigation/ViewModelFactories.kt` - add factory
- `app/src/main/java/com/example/localllmvoice/navigation/AppNavigation.kt` - wire VM into download route
- `app/src/main/java/com/example/localllmvoice/ui/onboarding/OnboardingDownloadScreen.kt` - full implementation

**OnboardingDownloadUiState + ViewModel changes (~70 lines):**
- UI state data class in the same file:

```kotlin
data class OnboardingDownloadUiState(
    val phase: Phase = Phase.Idle,
    val progressPercent: Int = 0,
    val errorMessage: String? = null,
) {
    enum class Phase { Idle, Downloading, Completed, Failed }
}
```

- `OnboardingDownloadViewModel(private val downloadAllModels: DownloadAllModelsUseCase) : ViewModel()`.
- `private val _uiState = MutableStateFlow(OnboardingDownloadUiState())`; expose `val uiState: StateFlow<...> = _uiState.asStateFlow()`.
- `fun startDownload()`:
  - Guard: if `_uiState.value.phase == Phase.Downloading` return.
  - Set phase `Downloading`, clear error.
  - `viewModelScope.launch { downloadAllModels().collect { event -> ... } }`.
  - Map `DownloadAllModelsEvent`:
    - `GemmaProgress(downloaded, total)` -> `progressPercent = if (total > 0) ((downloaded * 100) / total).toInt() else 0`, phase `Downloading`. (Gemma ~2.5GB dominates the bar; this is the intended approximation.)
    - `SttProgress(percent)` -> keep `progressPercent = percent.coerceIn(0, 100)`, phase `Downloading`.
    - `Completed` -> phase `Completed`, `progressPercent = 100`.
    - `Failed(message)` -> phase `Failed`, `errorMessage = message`.
- Do not call `onFinished` from the VM; the screen observes `Phase.Completed`.

**ViewModelFactories changes (~12 lines):**
- Add `OnboardingDownloadViewModelFactory(private val appContainer: AppContainer)` mirroring the existing factories, constructing `OnboardingDownloadViewModel(appContainer.downloadAllModelsUseCase)`.

**AppNavigation changes (~6 lines):**
- In the `ONBOARDING_DOWNLOAD` composable, construct the VM and pass it:

```kotlin
composable(Routes.ONBOARDING_DOWNLOAD) {
    val viewModel: OnboardingDownloadViewModel = viewModel(
        factory = OnboardingDownloadViewModelFactory(appContainer),
    )
    OnboardingDownloadScreen(
        viewModel = viewModel,
        onFinished = { finishOnboarding() },
    )
}
```

**OnboardingDownloadScreen changes (~70 lines):**
- New signature: `OnboardingDownloadScreen(viewModel: OnboardingDownloadViewModel, onFinished: () -> Unit, modifier: Modifier = Modifier)`.
- `val uiState by viewModel.uiState.collectAsStateWithLifecycle()`.
- Use `OnboardingScaffold` with:
  - `heading = "Placing the AI on your device..."`
  - `body = "To guarantee zero lag and total privacy, we are downloading your AI tutor's 'brain' directly to your phone (2.7GB, ~10 minutes). You will need Wi-Fi for this step. Once finished, you'll never need the internet to practice again."`
- Bottom `content` block:
  - When `phase == Downloading` OR `phase == Completed`: show a label row "DOWNLOADING BRAIN..." (use `MaterialTheme.typography.labelMedium`, `color = primary`) with `${uiState.progressPercent}%` right-aligned, then a `LinearProgressIndicator(progress = { uiState.progressPercent / 100f }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(percent = 50)))`, then the static caption "Strong connection required" (`labelMedium`, `onSurfaceVariant`).
  - `uiState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }`.
  - Primary CTA logic:
    - `Phase.Idle` or `Phase.Failed`: `OnboardingPrimaryButton(text = if (failed) "Retry download" else "Start download", onClick = viewModel::startDownload)`.
    - `Phase.Downloading`: `OnboardingPrimaryButton(text = "Downloading...", onClick = {}, enabled = false)`.
    - `Phase.Completed`: `OnboardingPrimaryButton(text = "Start talking", onClick = onFinished)`.
- Static copy "Wi-Fi required" / "Strong connection required" is display-only; no connectivity check.
- Do NOT auto-start the download; require the "Start download" tap (matches the screenshot).

**Tests:**
- `OnboardingDownloadViewModelTest` (JUnit + Turbine, per AGENTS.md): feed a fake `DownloadAllModelsUseCase` (extract an interface or pass a lambda-backed fake) emitting `GemmaProgress -> SttProgress -> Completed` and assert `uiState` transitions Idle -> Downloading(percent) -> Completed(100). Assert a `Failed` event sets `Phase.Failed` with the message. If extracting an interface for the use case is more than a trivial change, note it and keep the test at the event-mapping level.

**Acceptance criteria:**
- [ ] Screen renders heading/body per copy with "Start download" CTA and no visible progress bar initially.
- [ ] Tapping "Start download" starts the real model download and the bar + percent update.
- [ ] On completion the CTA becomes "Start talking"; tapping it finishes onboarding (flag set, lands on dashboard).
- [ ] A failed download shows the error and a "Retry download" CTA.
- [ ] If models are already present, the use case emits `Completed` quickly and the CTA shows "Start talking".

**Estimated total: ~160 lines, 4 files**
