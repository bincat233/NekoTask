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

## 📸 Screenshots
*(to be added — current UI demo with AI chat + manual add card)*

## 🛠️ Tech Stack
- **Android (Jetpack Compose)**
- **Kotlin**
- **Material 3 design**
- **Ktor** for networking
- **OpenAI GPT API** integration

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
- ❌ No persistence layer (no database).

---

## 🎯 Why NekoTask?
Many productivity tools are **overwhelming** — too many buttons, features, and settings.
For users with ADHD, this can create friction instead of support.

NekoTask’s goal is to:
- Keep **manual controls minimal**.
- Provide **AI-assisted task management**.
- Build a **friendly, motivating environment** with playful design.

---

## 💡 Roadmap
- [ ] Task persistence with local database.
- [ ] Accessibility improvements.
- [ ] Gamified streaks/rewards system.

---

> 🎓 *This project is currently developed as part of a Master's dissertation.*
