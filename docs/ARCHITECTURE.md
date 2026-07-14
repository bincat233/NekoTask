# NekoTask Architecture

This document outlines the core architecture, data flows, and AI integration layers of NekoTask.

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
- For upcoming architectural changes and refactoring plans, see `ARCHITECTURE_ROADMAP.md`.

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
- Chat UI currently only renders plain text; see `AGENT_UI_ROADMAP.md` for planned structured interactions (tool-call progress, retry, interactive choices, confirm/undo for destructive tool calls).

## Subtask Division

- Current implementation: `SubtaskDivisionCoordinator` asks the shared `LlmRuntime` for the current `PromptExecutor` and `LLModel`, then creates `SubtaskDivisionService` per call.
- `SubtaskDivisionService` chooses `AISubtaskDivider` or `MockSubtaskDivider` based on config.
- `AISubtaskDivider` uses a Koog agent with a required tool call to produce structured `SubtaskDivisionResponse`.
- Settings provides `useAI`, `maxSubtasks`, and `DivisionStrategy`; task detail passes these through `SubtaskDivisionEvent.CreateFromSuggestions`.
- See `SUBTASK_DIVISION.md` for the module-level notes.
