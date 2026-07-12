# Architecture Roadmap (July 13, 2026)

> **Scope Note:** This roadmap focuses exclusively on **module boundaries, encapsulation (deep modules), and responsibility segregation**. It does NOT cover data model readiness for cloud synchronization (e.g., global UUIDs, tombstone soft-deletes, CRDT-friendly `order_in_parent`). For sync-related data architecture gaps, please refer to `docs/AI_TASK_TOOLS.md`.

Based on the architecture review, the following improvements are planned for the NekoTask codebase to deepen modules and improve maintainability. 

## Execution Strategy & Dependencies

These tasks are not strictly parallel; they have a natural dependency graph. We recommend executing them in the following order to minimize friction:

1. **Phase 1: Foundation (Data & Tooling)**
   - **Task 3 (Seal Repository Seam)**: Do this first. By fixing the internal `AppDatabase` instantiation and leaking flows, we untangle `MainViewModel` (which currently punches through `TodoRepository.database` to build `LongTermMemoryRepository`).
   - **Task 4 (Separate AI Tool Outcomes)**: Independent of UI, can be done anytime.
2. **Phase 2: UI State Purity**
   - **Task 2 (Give Pickers Explicit Context)**: Make pickers pure before touching the root screen. Removing their implicit dependencies on `TaskDetailState` simplifies the state machine.
3. **Phase 3: Root Orchestration**
   - **Task 1 (Deepen Root Screen Interface)**: Do this last. With a clean data layer and pure pickers, we can safely refactor the root event routing without getting tangled in hidden state.

---

## 1. Deepen the Root Screen Interface (Completed)
Currently, `MainScreen` is tightly coupled to app-level routing, translating UI details into many specific section events.

- **Goal**: Deepen the root UI module. `MainScreen` should render state and emit high-level screen intents.
- **Implementation**: Move event routing and mode policy behind intent-handlers in `MainViewModel`. 
  - *Risk Mitigation*: To avoid creating a new "God Object" (a shallow `MainUIController` that just forwards 80 events), we should NOT stuff all logic into one file. Instead, `MainViewModel` should act as a pure dispatcher that delegates high-level intents (e.g., "User wants to create a task") to specialized Domain Orchestrators (e.g., a `TaskCreationFlow` that coordinates the Pickers and Repository), rather than micromanaging the UI state directly.
- **Benefits**: Localizes mode bugs, provides a single root interface, and allows tests to hit screen intents instead of granular UI events.

## 2. Give Pickers Explicit Context (Completed)
Picker modules (`DateTimePickerCoordinator`, `PriorityCoordinator`) currently hide a context switch, mutating either local form state or selected task state depending on `TaskDetailState.isVisible` (e.g., `handleEvent` in `DateTimePickerCoordinator`).

- **Goal**: Make pickers pure UI state modules that only emit selected values.
- **Implementation**: Move persistence logic into the explicit manual-add and task-edit flows that consume these picker values.
- **Benefits**: Simplifies picker interfaces, isolates edit rules, and makes testing easier by avoiding hidden state dependencies.

## 3. Seal the Task Repository Seam (Completed)
`TodoRepository` leaks implementation details by instantiating the Room `AppDatabase` internally and exposing legacy flows directly (e.g., `tasksStateFlow` which is manually synced and mostly unused externally). Furthermore, `MainViewModel` punches through `TodoRepository.database` to build `LongTermMemoryRepository`.

- **Goal**: Create a clean repository seam.
- **Implementation**: Inject `AppDatabase` or specific DAOs from the outside (e.g., in Application class). Remove the redundant `tasksStateFlow` state copy. Move state snapshots to caller-owned flows or a dedicated query module.
- **Benefits**: Hides Room implementation from callers, simplifies testing with a single adapter, stops manual state-sync bugs, and allows clean DI.

## 4. Separate AI Tool Outcomes from Text Protocol (Completed)
Internal control flow in `TaskToolSet` depends on external LLM tool string protocols (e.g., `.startsWith("ok")`).

- **Goal**: Decouple tool execution outcomes from the LLM text protocol.
- **Implementation**: Use typed internal outcomes (e.g., sealed classes) and only serialize to `"ok/error"` or JSON at the absolute return edge of each `@Tool` method.
- **Benefits**: Prevents protocol leaks into business logic and allows tests to assert on typed outcomes instead of strings.

## 5. Deepen subtask division (Completed)
**Current:** Coordinator orchestration + loop inserts. Loading state leaked to TaskDetail.
**Target:** 
- Workflow deep module (`SubtaskDivisionCoordinator` manages own `SubtaskDivisionState`).
- `TodoRepository` exposes `addDetailedSubtasks` for atomic batch insert.
- UI observes module's state.
