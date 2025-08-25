---
description: Create a concise, high-signal git commit message from current uncommitted changes
---

- __Goal__: Generate a commit message based on the actual diff. The agent should first inspect git status and diffs, then draft a subject + body, show them for approval, and only commit after confirmation.

1. __Inspect diffs to understand what changed__

- If there are staged changes:
// turbo
Run: git diff --staged --stat --patch --minimal
- If nothing is staged:
// turbo
Run: git diff --stat --patch --minimal

2. __Summarize changes__

- Identify scopes by directory/file paths (e.g., `app/src/...`, `build.gradle.kts`).
- Classify change types: feat, fix, refactor, chore, docs, test, build, ci.
- Note impact, user-visible effects, performance, and any breaking changes.

4. __Write the commit message__

5. __Show the message for approval__

- Print final Subject and Body in chat for confirmation.
