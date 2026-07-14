# Agent UI Structured Interaction Roadmap (July 14, 2026)

> **Scope note:** This roadmap is about the interaction layer between the chat UI and the AI agent — how tool calls, failures, and multi-option responses surface to the user in `ChatMessage`/`ChatBubble`. It is not about the tool protocol itself (see `docs/AI_TASK_TOOLS.md`) or subtask division (see `docs/SUBTASK_DIVISION.md`).

## Current State

- `ChatMessage` (`domain/entities/ChatMessage.kt`) is plain text only — no structured payload for options, tool progress, or task references.
- `ChatStreamEvent.ToolCallStarted` (`assistant/ChatAgent.kt`) is emitted but discarded (`else -> Unit` in `ChatCoordinator`) — tool execution is invisible to the user.
- `MessageStatus.Failed` renders an error bubble but there is no retry event anywhere in `ChatOverlayEvent`/`ChatCoordinator` — the user must retype the whole message.
- `TaskToolSet.delete_task` executes immediately, recursively, with no confirmation step and no undo.
- Streaming cancellation only happens via `awaitClose { job.cancel() }` (`TodoAgent.kt`) when the collector itself is torn down; there is no in-UI "stop generating" action.
- `buildChatPrompt` sends the full `history: List<ChatMessage>` every turn with no windowing/summarization.
- When the assistant references a task by title/ID, the mention is plain text with no tappable link back to that task.

None of this is broken — the agent works end-to-end — but the chat surface has no vocabulary beyond "text bubble," so every one of the gaps above degrades to either silence or a retyped message.

## Priority and Rationale

Ordered by risk/frequency, not by implementation size:

1. **Confirm/undo destructive actions** — highest risk. A misread instruction to `delete_task` can silently wipe a whole subtree with no recovery path, which cuts against the app's low-cognitive-load positioning.
2. **Tool-call progress visibility** and **retry on failure** — highest frequency. These are hit on nearly every multi-step AI action or transient network error.
3. **Interactive choice bubbles** — the recurring "model offers 2–3 options, user picks one" pattern currently degrades to a numbered text list the user retypes.
4. **Stop generation** — lower frequency but a real gap for long or wandering streamed replies.
5. **History windowing** and **clickable task references** — lower urgency at current data volumes (single local user, small task lists); revisit alongside any move to a networked backend (see the "known follow-ups" note in `docs/AI_TASK_TOOLS.md` about full-snapshot context).

## Roadmap

### 1. Confirmation + undo for destructive tool calls
**Current:** `delete_task` runs immediately and recursively; no confirmation, no undo.
- **Goal:** Give the user a chance to catch an AI-initiated deletion before or right after it happens.
- **Implementation:** Either (a) have `delete_task` return a pending-confirmation outcome that the UI renders as an inline confirm/cancel bubble before the repository call runs, or (b) keep immediate execution but add a time-boxed "Undo" action on the resulting chat bubble backed by the deleted subtree snapshot. (b) is less disruptive to the existing tool-calling flow and matches the manual-UI delete pattern more closely.
- **Benefits:** Removes the single highest-risk gap in AI-driven task management.

### 2. Tool-call progress visibility
**Current:** `ChatStreamEvent.ToolCallStarted` is defined but unused.
- **Goal:** Show the user what the agent is doing mid-turn (e.g. "Creating task…", "Updating 3 tasks…").
- **Implementation:** Wire `ToolCallStarted` into `ChatCoordinator`/`ChatOverlayReducer` as a transient status line on the in-progress assistant message, keyed off `toolName` with a small display-name map. No new message type needed — this augments the existing placeholder-message flow.
- **Benefits:** Turns multi-tool-call turns from a black box into visible progress; low implementation cost since the event already exists.

### 3. Retry for failed messages
**Current:** `MessageStatus.Failed` only swaps in error text; no retry event exists.
- **Goal:** Let the user retry a failed send without retyping.
- **Implementation:** Add a `ChatOverlayEvent.RetryMessage(id)` that re-runs `ChatCoordinator`'s send path using the original user message text (already available via `replyToId`/history lookup), replacing the failed placeholder in place.
- **Benefits:** Closes a dead-end UX state that currently has no recovery action.

### 4. Interactive choice bubbles
**Current:** No structured channel for the model to offer selectable options; multi-choice moments degrade to a text list.
- **Goal:** Let the model present 2+ options as tappable chips; tapping sends the selection as the next user turn.
- **Implementation:** Add a `present_choices(options: List<String>)` Koog tool (consistent with the project's tool-calling-only guardrail — no inline text-protocol parsing). Extend `ChatMessage` with an optional `options: List<String>?` populated once the tool call completes (non-streamed, since it's a discrete choice set, not token-by-token text). Render as chips in `ChatBubble` when present. Must follow the existing streaming + tool-call pitfall (`appendPrompt` the assistant response before the next step) documented in `CLAUDE.md`.
- **Benefits:** Removes the most common friction point raised in review — the model already produces multi-option answers, this just gives them a first-class UI instead of a text list.

### 5. Stop generation
**Current:** Cancellation only happens implicitly when the collecting coroutine is torn down.
- **Goal:** Give the user an explicit "stop" action during a long or wandering streamed reply.
- **Implementation:** Add a stop affordance in the fullscreen/peek input bar while `MessageStatus.Sending`, wired to cancel the same collection job `TodoAgent.chatStreaming`'s `awaitClose` already tears down — no new cancellation plumbing needed, just an explicit trigger for the existing one.
- **Benefits:** User control over runaway or off-track responses without leaving the chat.

### 6. History windowing / summarization
**Current:** `buildChatPrompt` sends the full message history every turn.
- **Goal:** Bound prompt growth for long conversations.
- **Implementation:** Not urgent at current scale (local single-user, short-lived chats). Revisit if/when a networked backend or long-lived conversation history makes full-history replay a real token-cost or context-limit problem — same caveat already recorded for the full `CURRENT_TODO_STATE` snapshot in `docs/AI_TASK_TOOLS.md`.
- **Benefits:** Deferred; tracked here so it isn't rediscovered from scratch later.

### 7. Clickable task references in chat
**Current:** The assistant mentions tasks by title/ID as plain text.
- **Goal:** Let the user tap a task mention in a chat bubble to jump to that task's detail view.
- **Implementation:** Requires the same kind of structured `ChatMessage` extension as item 4 (e.g. inline reference spans resolved against `CURRENT_TODO_STATE` IDs) plus navigation wiring from the chat overlay into `TaskDetailCoordinator`. Best done after item 4 lands, so both features share one structured-content extension to `ChatMessage` instead of two separate ones.
- **Benefits:** Closes the loop between "AI talks about a task" and "user can act on it," without leaving the chat surface.
