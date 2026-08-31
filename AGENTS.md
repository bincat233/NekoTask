## Project: NekoTask (`~/AndroidStudioProjects/todolist`)

This file is the agent/developer maintenance guide. It records **how / guardrails / implementation facts**. Product positioning, motivation, and public-facing introduction belong in `README.md`. English project documentation is preferred. If documentation and code disagree, trust the code first, then update stale docs.

**Type**: Android app (Jetpack Compose + Kotlin)

**Positioning**: An AI-assisted, ADHD-friendly to-do app with a cat theme, focused on low cognitive load.

**Package**: `me.superbear.todolist`

## AI Collaboration Heuristics

- **Assess Task Scope**: For local bug fixes, simple UI tweaks, or explicit user commands, directly modify the code to maintain agility. For cross-module refactoring, architectural changes, or ambiguous requests, propose a plan first and wait for user confirmation before executing.
- **Context Distillation**: All discovered pitfalls, non-obvious bugs, and new architectural patterns MUST be captured and distilled into this document (or `docs/`). Routine, minor daily changes do not need to be documented as long as they do not conflict with existing documentation.

## Current Product Surface

- Manual task creation: Material 3 bottom card, two-step date/time picker, priority selection.
- Task tree: `parentId` and `orderInParent` represent hierarchy and sibling order.
- AI chat: natural-language task creation/update/delete/query, with peek bubbles and fullscreen chat.
- AI subtask division: breaks larger tasks into executable small steps.
- Long-term memory: `LongTermMemory` stored in Room, managed from Settings, and maintainable by AI tools.
- Debug sample data: debug wiring seeds examples and exposes a reset option in Settings.

## Architecture

The detailed architecture, including data layers, AI coordination, and module boundaries, has been split into a dedicated document to keep this file concise.

👉 **[View NekoTask Architecture](docs/ARCHITECTURE.md)**
👉 **[View Architecture Roadmap](docs/ARCHITECTURE_ROADMAP.md)**

## Configuration

- Debug builds can read a default OpenAI key from `local.properties`:

```properties
OPENAI_API_KEY=...
```

- Release builds do not embed the developer key. Users enter their own provider key in Settings.
- Settings stores provider-prefixed keys in SharedPreferences, for example `openai_api_key`.
- `OPENAI_BASE_URL` and `OPENAI_MODEL` currently generate BuildConfig fields, but chat runtime behavior is mainly controlled by Settings / `SettingsCoordinator.PROVIDER_INFO`.

## Development Guardrails

- `README.md` describes what and why; avoid volatile internal call chains there. Put implementation details in this file or module docs.
- New UI text belongs in `strings.xml` and `values-zh/strings.xml`.
- When adding AI-facing text, also consider language alignment in prompts; UI translation alone is not enough.
- New task write paths must account for `orderInParent` and sibling reindexing. Do not insert bare tasks without considering order.
- Coordinator task-write failures should use the shared `ui.main.sections.logFailure` helper instead of repeating local `Log.e` blocks.
- When deleting a parent task, prefer the recursive deletion path to avoid orphaned subtasks.
- Keep task tree status rules in `TodoRepository`, not individual UI coordinators, so AI tools and manual UI interactions stay consistent.
- Changes involving Settings provider/model/key should also check `TodoAgent`, `LlmRuntime`, and `SubtaskDivisionCoordinator`, because subtask division reuses the current AI runtime.
- Do not turn debug sample reset into "wipe on every install"; data should be cleared only by explicit developer action, app data clearing, or uninstall.
- Do not use README's historical roadmap to decide whether a feature exists. Confirm with `rg` and code.

## Known Pitfalls

### Environment And Dependencies

- `androidx.lifecycle:2.11.0` and `androidx.core:1.19.0` or newer require `compileSdk >= 37`.
- Even with an all-Compose UI, if `themes.xml` inherits from `Theme.Material3.*`, keep `com.google.android.material:material`; otherwise AAPT resource linking may fail.
- `minSdk = 26` (raised from 23 in July 2026). Root cause: `ai.koog:agents-core-android`'s hard dependency on `ai.koog:serialization-jackson` pulls in `jackson-module-kotlin:2.21.3`, whose Kotlin value-class boxing/unboxing codegen uses `MethodHandle.invokeExact` — D8 refuses to dex that below API 26. This is a real bytecode constraint, not a manifest-declared minSdk that can be worked around with `tools:overrideLibrary`.
- **Koog dependency version pitfall**: `app/build.gradle.kts` pins Koog artifacts to an exact version (currently `1.0.0-SNAPSHOT`, matching a locally `publishToMavenLocal`'d build in `~/.m2/repository/ai/koog/`). If this ever gets changed to a bare `1.0.0` (no `-SNAPSHOT`), Gradle will silently resolve a *different*, independently-published `1.0.0` artifact from a remote repo instead of the local snapshot — same version-looking string, different actual code. This was discovered when the remote `1.0.0`'s AARs declared `minSdkVersion=35` in their manifests while the local snapshot's declared `23`. Always verify which repo a Koog artifact actually resolved from (`./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep ai.koog`) before trusting its declared minSdk — an AAR's `<uses-sdk>` value is asserted by the publisher, never verified by dexing, since dexing only happens when an app assembles, not when a library publishes.

### Initialization Order

- Kotlin properties initialize in declaration order.
- Be careful with `MainViewModel` coordinator and `MutableStateFlow` ordering. Do not access a property from `init` before it has been initialized.

### Koog Streaming Tool Calls

- Koog `requestLLMStreaming()` does not append the streamed assistant response back into the prompt history for you. If a streaming agent can call tools, convert the stream to a message response and explicitly `appendPrompt { message(response) }` before the next LLM/tool-result step. Otherwise OpenAI-compatible APIs can reject later requests because `tool` role messages no longer follow a recorded assistant `tool_calls` message.
- When adapting a stream into `callbackFlow`, do not `close(e)` after emitting a user-visible error event unless the collector is expected to fail. Closing with the throwable rethrows into the collecting coroutine and can crash the app even when the UI handled the error state.

### Agent Trajectory Logging

- `TodoAgent.AgentTraceFeature` (private object in `TodoAgent.kt`) logs every node's input/output and every tool call's args/result under logcat tag `AgentTrace`, using Koog's pipeline hooks (`interceptNodeExecutionStarting/Completed/Failed`, `interceptToolCallStarting/Completed/Failed`) rather than hand-added logging inside each node. It's installed unconditionally in `buildAgent()`, so it covers both `chat()` (built-in `singleRunStrategy()`) and `chatStreaming()` (custom `streamingSingleRunStrategy()`) without duplicating logic per strategy.
- Node-level logging deliberately skips `__start__`, `__finish__`, and any node whose name contains `ExecuteTool`: the first two are pure passthrough duplicates of the adjacent real node, and the tool-execution node's `ReceivedToolResults.toString()` includes a large static `toolDescription` field that eats the whole truncation budget before reaching the actually-useful `output`/`result` fields. The `tool=... CALL/RESULT` lines from the tool-call intercepts already cover that information more cleanly.
- If you need to inspect a real agent run without the Android app UI, this is the fastest path — no need to build a separate harness first.

### Koog ToolSet Parameter Types

- **Never use `String` as a parameter type for structured/list data in a `ToolSet` method.** When the LLM calls the tool, the whole parameter payload is already a JSON object. Koog's `JSONSerializer` will try to deserialise the LLM-supplied JSON array/object *directly* into the declared Kotlin type. If the declared type is `String` but the LLM (correctly) sends a JSON array, deserialization fails silently, the tool returns an error string, and the LLM retries until `AIAgentMaxNumberOfIterationsReachedException` is thrown — leaving `lastResult == null` and no subtasks created.
- **Fix**: declare the parameter as a typed list or data class (e.g., `subtasks: List<SubtaskInput>`) and annotate the data class with `@Serializable`. Koog will then deserialise the LLM payload natively without any manual `kotlinx.serialization` parsing inside the tool body.
- This was the root cause of the `DivideTaskToolSet.divide_task` bug (July 2026). See `docs/SUBTASK_DIVISION_BUG_REPORT.md`.

### Instrumentation Tests

- `InstrumentationRegistry.getInstrumentation().context.applicationContext` can be null for the test package context. Repository tests that need an application context should use `targetContext`, or a small `ContextWrapper` whose `applicationContext` returns itself.
- If a repository test must create a real Room database, redirect `getDatabasePath()` to a test-only cache directory so the test cannot wipe the app's real database.
- JUnit4 `@Before`, `@After`, and `@Test` methods should use block bodies when wrapping `runBlocking`; expression bodies may compile but appear non-void to the Java runner and fail test initialization.

### Long-Term Memory

- Long-term memory uses two channels: Room-backed prompt context injection and Koog tool calling.
- Manual channel: Settings edits memory through `LongTermMemoryCoordinator`.
- AI channel: `MemoryToolSet` exposes memory tools. No separate automatic extraction agent is needed.

### Sample Data

- `SeedManager` is injected only in debug production wiring. Release builds and tests that construct `TodoRepository` without a `SeedManager` do not seed sample data.
- The seed marker is `seed_done_v1`; if sample content changes and old installs should reseed, bump the suffix.
- Settings debug-only developer reset chain: `MainViewModel.resetSampleData()` -> `TodoRepository.resetSampleData()` -> `SeedManager.resetAndReseed()`.

### Real-Device Debugging And Screenshot Guidelines

When capturing screenshots or performing UI automation on a physical device:

- **Prevent Screen Lock & Timeout**: Since physical devices might lock automatically, temporarily disable timeout and keep the screen awake when plugged into USB:
  ```bash
  adb shell settings put system screen_off_timeout 1800000
  adb shell settings put global stay_on_while_plugged_in 3
  ```
  Always restore defaults (e.g., `60000` and `0`) once finished to avoid battery drain.
- **IME Composition Pitfalls**: Using `adb shell input text` on a device with a stateful IME (like a Chinese Pinyin keyboard) can cause entered characters to be intercepted by predictive text, resulting in unintended selections. 
  - To bypass this, send a `66` (KEYCODE_ENTER) keyevent right after `input text` to commit the characters.
  - Alternatively, temporarily switch to a basic English IME using `adb shell ime set`.
- **Jetpack Compose Frame Settling**: Ensure the screen has fully updated and the keyboard has finished dismissing before taking screen captures to avoid blurred overlay animations or open soft keyboards.
