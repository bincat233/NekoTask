## Project: NekoTask (`~/AndroidStudioProjects/todolist`)

This file is the agent/developer maintenance guide. It records **how / guardrails / implementation facts**. Product positioning, motivation, and public-facing introduction belong in `README.md`. English project documentation is preferred. If documentation and code disagree, trust the code first, then update stale docs.

**Type**: Android app (Jetpack Compose + Kotlin)

**Positioning**: An AI-assisted, ADHD-friendly to-do app with a cat theme, focused on low cognitive load.

**Package**: `me.superbear.todolist`

## Current Product Surface

- Manual task creation: Material 3 bottom card, two-step date/time picker, priority selection.
- Task tree: `parentId` and `orderInParent` represent hierarchy and sibling order.
- AI chat: natural-language task creation/update/delete/query, with peek bubbles and fullscreen chat.
- AI subtask division: breaks larger tasks into executable small steps.
- Long-term memory: `LongTermMemory` stored in Room, managed from Settings, and maintainable by AI tools.
- Debug sample data: debug wiring seeds examples and exposes a reset option in Settings.

## Current Implementation Architecture

- Entry point: `MainActivity` sets Compose content and renders `MainScreen`.
- UI: Jetpack Compose + Material 3, single Activity, no Compose Navigation.
- Main coordination: `MainViewModel` is no longer the whole business object. It wires feature coordinators together, aggregates their state, and routes `AppEvent`.
- Current coordinators:
  - `AppShellCoordinator`: app-level page state for the task list/settings shell.
  - `TaskListCoordinator`: task list Flow, task CRUD orchestration, current task snapshot for AI.
  - `TaskDetailCoordinator`: detail sheet state, debounced title/content writes, deletion, loading markers.
  - `ManualAddCoordinator`: manual add form and submit flow.
  - `DateTimePickerCoordinator` / `PriorityCoordinator`: shared transient picker state for manual add and task detail; when detail is visible, selections persist directly to that task.
  - `ChatCoordinator`: chat message state, peek/fullscreen state, calls into `ChatAgent`.
  - `SubtaskDivisionCoordinator`: subtask division orchestration, creates `SubtaskDivisionService` on demand.
  - `LongTermMemoryCoordinator`: long-term memory CRUD and memory list observation.
  - `SettingsCoordinator`: AI provider/key/model, subtask division preferences, language switching.
- Root state: `AppState` aggregates section states; `SettingsState` is built separately by combining settings and long-term memory list state.
- `MainScreen` still owns rendering, drawer open/close state, overlay/sheet/date picker placement, and event wiring. App page state and back-action priority live in the app shell module.

## Data Layer

- Room is the task data source of truth.
- Database: `AppDatabase`, schema version `4`.
- Tables: `tasks`, `long_term_memories`.
- View: `unfinished_tasks`.
- Task model: `TaskEntity` includes `id`, `title`, `content`, `status`, `priority`, `createdAt`, `updatedAt`, `dueAt`, `parentId`, and `orderInParent`.
- Domain model: `Task` uses `kotlinx.datetime.Instant`; Room stores times as epoch milliseconds through `InstantConverter`.
- `TodoRepository.tasks` is the Room Flow; `tasksStateFlow` is a legacy bridge and is still used for UI-friendly snapshot queries.
- Task write APIs are `suspend` and return explicit `Result` values. Keep coroutine ownership in callers/coordinators instead of adding new fire-and-forget writes inside the repository.
- Completing a parent task cascades `DONE` to all descendants in one repository transaction; reopening a parent does not recursively reopen children.

## AI Layer

- AI framework: JetBrains Koog (`ai.koog:agents-core-android`).
- Production implementation: `TodoAgent` implements both `ChatAgent` and `LlmRuntime`.
- Providers: OpenAI and DeepSeek, selected through Settings by provider/model/key.
- Executor: `TodoAgent.buildExecutor()` returns `MultiLLMPromptExecutor`; OpenAI-compatible clients use `KtorKoogHttpClient` to adapt Ktor into Koog.
- Tools: `TaskToolSet` exposes task create/update/delete/complete tools; `MemoryToolSet` exposes long-term memory add/update/delete/list tools.
- Checklist/planning requests with multiple actionable items should use `TaskToolSet.add_task_with_subtasks` for a new parent task, or `TaskToolSet.add_subtasks` for an existing parent. Do not encode multi-item checklists as numbered text in task `content`.
- Prompt context: chat injects `CURRENT_TODO_STATE` plus optional `MEMORY CONTEXT`, and uses `Locale.getDefault().language` to request Chinese or English replies.
- `ChatAgent` is the chat-only seam. `LlmRuntime` is the provider/model/executor seam used by Settings and subtask division.
- Guardrail: do not restore the old strict JSON action client. Current action execution is Koog tool calling.

## Subtask Division

- Current implementation: `SubtaskDivisionCoordinator` asks the shared `LlmRuntime` for the current `PromptExecutor` and `LLModel`, then creates `SubtaskDivisionService` per call.
- `SubtaskDivisionService` chooses `AISubtaskDivider` or `MockSubtaskDivider` based on config.
- `AISubtaskDivider` uses a Koog agent with a required tool call to produce structured `SubtaskDivisionResponse`.
- Settings provides `useAI`, `maxSubtasks`, and `DivisionStrategy`; task detail passes these through `SubtaskDivisionEvent.CreateFromSuggestions`.
- See `docs/SUBTASK_DIVISION.md` for the module-level notes.

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

### Initialization Order

- Kotlin properties initialize in declaration order.
- Be careful with `MainViewModel` coordinator and `MutableStateFlow` ordering. Do not access a property from `init` before it has been initialized.

### Koog Streaming Tool Calls

- Koog `requestLLMStreaming()` does not append the streamed assistant response back into the prompt history for you. If a streaming agent can call tools, convert the stream to a message response and explicitly `appendPrompt { message(response) }` before the next LLM/tool-result step. Otherwise OpenAI-compatible APIs can reject later requests because `tool` role messages no longer follow a recorded assistant `tool_calls` message.
- When adapting a stream into `callbackFlow`, do not `close(e)` after emitting a user-visible error event unless the collector is expected to fail. Closing with the throwable rethrows into the collecting coroutine and can crash the app even when the UI handled the error state.

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
