# Termux Integration Roadmap (July 14, 2026)

> **Scope note:** This is a speculative power-user feature, not yet started. It covers letting NekoTask trigger commands in [Termux](https://termux.dev/) via Android Intents. It is unrelated to the AI tool protocol (`docs/AI_TASK_TOOLS.md`) or the chat-UI interaction gaps (`docs/AGENT_UI_ROADMAP.md`), except where noted in Phase 3.

## Current State

- No Intent-based integration with any external app exists today. `AndroidManifest.xml` declares only `INTERNET` and a single exported launcher activity — no `RUN_COMMAND` permission, no broadcast receiver, no `PendingIntent` result plumbing.
- This is a from-scratch feature area, not an extension of an existing module.

## Why

Power users who already run Termux want to wire task actions to shell scripts/CLI tools NekoTask can't and shouldn't reimplement — e.g. syncing files, hitting a personal API, running a backup script, or triggering a home-automation command when a task completes. This is the same niche Tasker's Termux plugin serves on Android.

## Background: How Termux Intent Integration Works

- Termux exposes `com.termux.RUN_COMMAND`, targeting `com.termux/com.termux.app.RunCommandService`, with extras for executable path, arguments, working directory, and whether to run in the background vs. opening a visible session.
- The calling app must declare `<uses-permission android:name="com.termux.permission.RUN_COMMAND"/>` and the user must grant it.
- Termux itself must have `allow-external-apps=true` set in `~/.termux/termux.properties` — this is a Termux-side setting outside NekoTask's control, so onboarding needs to detect/explain it rather than assume it.
- Background commands can return a result (stdout/stderr/exit code) via a `PendingIntent` extra, letting the calling app show completion status instead of firing-and-forgetting.
- **Implementation caveat:** verify the exact extra key names and result payload shape against Termux's current RUN_COMMAND documentation before writing code — the mechanism above is right in shape, but Termux has changed extra names across versions before.

## Security Posture (read before implementing any of this)

This feature is arbitrary command execution on the user's device. Two very different trust boundaries must not be blurred:

- **User-authored commands, manually triggered** — the user types/pastes a command themselves and taps a button to run it. Low risk: it's the same trust level as the user opening Termux directly.
- **AI-agent-invokable commands** — if this were ever exposed as a Koog `@Tool` the model can call on its own, it becomes a prompt-injection target: a malicious task title, memory entry, or web-search result (via `SearchToolSet`) could steer the model into running a destructive or exfiltrating command with the user never having typed it.

**Recommendation: do not expose this to the AI agent in v1.** Ship it as a manual, user-authored automation feature only. If an AI-callable version is ever considered later, it must (a) run only against a user-pre-approved allowlist of saved command templates, never a freeform string the model composes, and (b) require an explicit per-call confirmation bubble — reusing the same confirm/undo pattern already planned for `delete_task` in `docs/AGENT_UI_ROADMAP.md` item 1.

## Roadmap

### 1. Manual command binding (no AI involvement)
**Goal:** Let a user attach a saved shell command to a task and run it on demand.
- **Implementation:** New `uses-permission` for `RUN_COMMAND`; a small settings/task-detail affordance to save a command (path + args) per task; a "Run in Termux" action that fires the `RUN_COMMAND` intent via `RunCommandService`. Store bindings locally (new Room column or side table), gated behind a Settings toggle since most users don't have Termux installed.
- **Benefits:** Delivers the core value (task-triggered automation) with the smallest trust surface — nothing the user didn't explicitly author runs.

### 2. Execution status feedback
**Current:** Phase 1 is fire-and-forget from the app's point of view.
- **Goal:** Show success/failure and captured output back in the task detail view.
- **Implementation:** Wire the `PendingIntent` result extra from Phase 1's background commands into a small result store keyed by task ID; surface as a status line ("Last run: exit 0, 2 minutes ago") with expandable stdout/stderr.
- **Benefits:** Turns the feature from blind triggering into something the user can actually trust and debug.

### 3. AI-facing exposure (deferred, not started)
**Goal:** Let the assistant suggest or run a pre-approved automation as part of a conversation.
- **Implementation:** Only after Phase 1/2 are stable and only against the allowlist-of-saved-templates model described above, with a mandatory confirm bubble per invocation — do not add a freeform `run_termux_command(command: String)` tool.
- **Benefits:** Deferred deliberately; listed here so the security constraint is on record before anyone is tempted to take the shortcut of a freeform tool.
