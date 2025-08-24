# 🐾 NekoTask

NekoTask is a playful yet practical **to-do list app with an AI-powered assistant** 🐱.
It was originally designed to support **people with ADHD** (adults or children, any age) by reducing **cognitive load** in task management — but of course, everyone can use it to stay organized.

⚠️ **Important Note**:
At this stage, **NekoTask is a functional prototype**.
- Tasks are loaded from a local JSON file (`app/src/main/assets/todolist_items.json`).
- The AI chat logic is implemented, with both a mock and a real client.
- The real client requires an OpenAI API key to function.

---

## ✨ Features
- **Dual Interaction Modes**
  - 📝 **Manual Mode**: Add tasks via a clean, minimal bottom card.
  - 🤖 **AI Chat Mode**: A functional chat interface to add, update, and delete tasks using natural language.
- **ADHD-Friendly UI Principles**
  - Minimal, distraction-free interface.
  - Reduced decision fatigue — AI agent simplifies task entry.
  - Playful cat theme for motivation.
 - **AI Subtask Decomposition (Core Idea)**
   - Ask the assistant to break a big task into small actionable subtasks.
   - Uses a strict JSON action contract to add tasks (optionally referencing a `parentId`).
 - **Due Date Picker (Two-step)**
   - Material 3 DatePicker + TimePicker with a two-step flow (date → time).
   - Quick visibility of due-state; button indicates selection.
 - **Chat Overlay Modes**
   - Peek bubbles over the list, or switch to fullscreen chat.
   - Pin/auto-dismiss behavior to reduce noise.
 - **AI Notepad (Working-Memory Notes)**
   - Quick, lightweight notes for facts/clues/context that are not actionable tasks.
   - Pin important notes; optionally link a note to a task; convert note → task when needed.
   - Clean separation from the checklist to avoid clutter.

## 📸 Screenshots
*(to be added — current UI demo with AI chat + manual add card)*

## 🛠️ Tech Stack
- **Android (Jetpack Compose)**
- **Kotlin**
- **Material 3 design**
- **Ktor** for networking
- **OpenAI GPT API** integration
 - **kotlinx.serialization** for robust JSON parsing
 - **kotlinx.datetime** for time handling

## 🔑 Setup
To use the AI features, you will need to provide your own OpenAI API key.

1.  **Create a `local.properties` file** in the root directory of the project if you don't already have one.
2.  **Add your OpenAI API key** to the `local.properties` file:
    ```properties
    openai_key="YOUR_API_KEY"
    ```
3.  **Make sure `local.properties` is in your `.gitignore` file** to prevent your API key from being committed to version control.

## 🚧 Current Status
- ✅ UI for task lists, AI chat bubbles, and manual add card.
- ✅ Sample cat-themed task data for testing.
- ✅ Tasks loaded from a local JSON file.
- ✅ AI backend integration with mock and real clients.
 - ✅ Two-step due date selection (date → time) wired to task creation.
 - ✅ Strict JSON contract for AI responses (say + actions) with parsing and safety checks.
 - ❌ No persistence layer yet (no database; in-memory only).

---

## 🎯 Why NekoTask?
Many productivity tools are **overwhelming** — too many buttons, features, and settings.
For users with ADHD, this can create friction instead of support.

NekoTask’s goal is to:
- Keep **manual controls minimal**.
- Provide **AI-assisted task management**.
- Build a **friendly, motivating environment** with playful design.
 - Turn big, vague tasks into small, executable steps with **AI subtask decomposition**.

---

## 💡 Roadmap (Next 10 days focus + beyond)

### Near-term (aimed for the current sprint)
- [ ] Subtasks S1 (in-memory):
  - `Task` model gains `parentId` (flat storage; UI groups by parent).
  - Expandable subtasks under each parent with inline add and quick complete.
  - Parent progress indicator (e.g., 2/5).
- [ ] AI contract update:
  - `add_task` supports optional `parentId` for AI-generated subtasks.
  - Keep strict single-JSON envelope (say + actions) for reliability.
- [ ] Time presets:
  - Quick presets like Tonight / Tomorrow / This week alongside the two-step picker.
 - [ ] Configurable AI Persona:
   - Presets: Neutral (default), Coach, Cheerful Cat, Minimalist.
   - Persona influences only `say` tone, never `actions` schema.
 - [ ] Energy level (Low/Medium/High):
   - Simple self-rating chip; drives “next small step” granularity and optional UI contraction (show only next actionable).
 - [ ] Privacy toggle for AI snapshot:
   - Off by default. When on, send a minimal, non-sensitive task snapshot in a system message.
 - [ ] AI Notepad MVP (in-memory):
   - New `Note` model and in-memory repository; CRUD + pin/unpin; optional link to `taskId`.
   - Notepad UI (sheet or screen) and a small pinned preview strip on Home.
   - Convert note → task (simple UI action); keep notes separate from the checklist.
 - [ ] Chat + Notes integration:
   - “remember …/记一下 …” maps to `add_note`; retrieval answers appear in `say`.

### Infrastructure
- [ ] Local persistence with Room (entities/DAOs, v1 schema; replace JSON-only).
- [ ] Compose Navigation + Task Details screen (edit title/notes/due/priority; manage subtasks).
 - [ ] Room for notes (separate `notes` table) and repository abstraction.

### Assistant & Privacy
 - [ ] Privacy toggle: choose whether to send a current-task snapshot to the AI (with clear copy; default off).
 - [ ] Confirmation gates for destructive actions (bulk delete/complete) and guardrails around overdue bulk ops.
 - [ ] Separate privacy toggle for notes snapshot (default off) with simple redaction.

### Reminders & Input
 - [ ] Gentle reminders with WorkManager (due and overdue notifications; rate limited, quiet hours friendly).
 - [ ] Focus 20-min mode (Pomodoro-like) with gentle start/stop cues.
 - [ ] In-app voice input for quick add; App Shortcuts for “Quick Add”.

### Later
- [ ] Accessibility improvements (large touch targets, high contrast, single-hand mode).
- [ ] Optional light gamification (streaks/stickers) — low-arousal, can be turned off.
- [ ] Advanced search/filter/sort; calendar views.
- [ ] Wearable and assistant integrations (Android first).

---

## 🤖 AI Contract (Brief)
The real client sends a system prompt requiring the assistant to reply with a single compact JSON object:

```json
{
  "say": "string",
  "actions": [
    { "type": "add_task", "title": "string", "notes": "string?", "dueAt": "ISO-8601?", "priority": "LOW|MEDIUM|HIGH|DEFAULT?", "parentId": 123 },
    { "type": "complete_task", "id": 123 },
    { "type": "delete_task", "id": 123 },
    { "type": "update_task", "id": 123, "title": "strin?", "notes": "string?", "dueAt": "ISO-8601?", "priority": "LOW|MEDIUM|HIGH|DEFAULT?", "parentId": 123 }
  ]
}
```

Responses are parsed and mapped to app actions. Mock and real clients are both supported.

Note actions (for working-memory notes) extend the same `actions` array without changing the envelope:

```json
{
  "say": "string",
  "actions": [
    { "type": "add_note", "text": "string", "taskId": 123, "pinned": true },
    { "type": "update_note", "id": 1, "text": "string", "pinned": false, "taskId": 123 },
    { "type": "delete_note", "id": 1 },
    { "type": "pin_note", "id": 1, "pinned": true }
  ]
}
```

---

## 🔬 Research Notes (for the dissertation)
- Primary research questions (examples):
  - Does AI-assisted subtasking reduce “time-to-start” (time to complete the first subtask)?
  - Do subtasks + progress visualization improve completion rate and reduce overdue rate?
  - Does a strict JSON contract increase action reliability (parse success and execution rate)?
- Suggested metrics:
  - Parse success rate; action execution success; undo rate.
  - Start time; completion/overdue rates; clicks per operation.
  - Short subjective scales (SUS, simplified NASA-TLX).
- Ethics & privacy:
  - No medical claims. Use “support level” language, not diagnosis.
  - Snapshot-to-AI toggle defaults to off; users can opt in.

---

> 🎓 *This project is currently developed as part of a Master's dissertation — and because I also live with ADHD and depression, I've taken it personally.*
