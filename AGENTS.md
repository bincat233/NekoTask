## 项目：NekoTask（~/AndroidStudioProjects/todolist）

**类型**：Android 应用（Jetpack Compose + Kotlin）

**定位**：面向 ADHD 用户的 AI 驱动待办事项应用，猫咪主题，主打低认知负担。

**核心功能**：

- 手动添加任务（Material 3 底部卡片，两步日期/时间选择器，优先级选择）
- AI 聊天模式（自然语言增删改查任务，支持 peek 气泡与全屏两种模式）
- AI 子任务拆解（将大任务分解为可执行小步骤）
- 长期记忆（LongTermMemory 实体，存于 Room 数据库，在 Settings 页管理）

**技术栈**：

- UI：Jetpack Compose + Material 3，单 Activity（MainActivity）
- 状态管理：MVI 模式（Event / State / Reducer per section）；MainViewModel 统一协调
- AI 框架：JetBrains Koog（`ai.koog:agents-core-android`），`AIAgent` + `ToolRegistry`，支持 OpenAI / DeepSeek 多供应商（`MultiLLMPromptExecutor`），底层 HTTP 仍是 Ktor，通过 `KtorKoogHttpClient` 适配进 Koog
- 数据库：Room v4，表 `tasks` + `long_term_memory`，视图 `unfinished_tasks`
- AI 客户端：`ChatAgent` 接口（`assistant/ChatAgent.kt`），生产实现 `TodoAgent`（Koog `AIAgent`，工具集 `TaskToolSet` + `MemoryToolSet`），测试假实现 `FakeChatAgent`（仅 androidTest）。`MainViewModel` 构造函数注入 `ChatAgent`，生产装配走 `MainViewModel.Factory`
- 子任务拆分：`AISubtaskDivider` + `SubtaskDivisionService`，构造函数注入 Koog `PromptExecutor`/`LLModel`（旧的 `TextAssistantClient`/`AssistantClient` 已整体删除）
- 序列化：kotlinx.serialization；时间：kotlinx.datetime（Instant 存为 Long epoch ms）
- Firebase AI SDK 已引入依赖（libs.versions.toml），但当前 AI 客户端仍用 OpenAI

**任务数据模型**（`TaskEntity`）：id、title、content、status(OPEN/DONE)、priority、createdAt、updatedAt、dueAt、parentId（树形结构）、orderInParent

**配置**：OpenAI API Key 通过 `local.properties` 注入（`openai_key`），`BuildConfig.OPENAI_API_KEY`

**包名**：`me.superbear.todolist`

**当前状态（2026-03-09）**：功能原型，Room 持久化已有，UI 完整，AI 集成可用。

---

## 开发坑点与经验落盘

### 1. 环境与依赖
- **SDK 版本**：`androidx.lifecycle:2.11.0` 及 `androidx.core:1.19.0` 以上版本强制要求 `compileSdk` 至少为 **37**。若编译报错 AAR Metadata 不匹配，需升级 `build.gradle.kts`。
- **混合 UI 架构的主题坑**：即便全量使用 Compose，如果 `themes.xml` 继承了 `Theme.Material3.*`（用于支持传统的 Dialog 或系统 UI 渲染），必须显式引入 `com.google.android.material:material` 依赖，否则 AAPT 资源链接会失败。

### 2. 初始化顺序（重要！）
- **ViewModel 陷阱**：在 Kotlin 中，属性按声明顺序初始化。
    - **坑点**：如果在 `init { ... }` 块中调用了某个方法（如 `loadTasks()`），而该方法访问了定义在 `init` 之后的属性（如 `_appState`），会导致 `NullPointerException` 闪退。
    - **经验**：始终将 `MutableStateFlow` 等关键状态属性放在 `init` 块的最上方。

### 3. AI 应用的国际化 (i18n)
- **UI 文本**：遵循 Android 标准，抽取到 `strings.xml` 和 `values-zh/strings.xml`。
- **AI 响应对齐**：对于 AI 驱动的应用，仅翻译 UI 是不够的。必须在发送给 AI 的 **System Prompt** 中包含语言指令。
    - **实现**：通过 `Locale.getDefault().language` 判断当前环境，在 Prompt 结尾追加 `"Please reply in Chinese."` 等指令，确保 AI 回复语言与系统语言一致。

### 4. 长期记忆 (Long-Term Memory) 机制
- **实现方案**：Room 数据库存储实体 + 对话前置上下文注入 + Koog 工具调用双通道。
- **手动通道**：用户在 Settings 页手动增删改，`MainViewModel` 观察 `LongTermMemoryRepository.getAllMemories()` 实时反馈；每次对话前 `getMemoryContextForAI()` 把激活记忆拼成文本块注入 Prompt（`MEMORY CONTEXT` 段）。
- **AI 自主通道**：参考主流实现（ChatGPT/Claude 的记忆功能都是工具调用模式，非独立提炼 agent），`MemoryToolSet`（`assistant/MemoryToolSet.kt`）暴露 `add_memory`/`update_memory`/`delete_memory`/`list_memories` 给 `TodoAgent` 的 `ToolRegistry`，AI 在对话中可自主判断并调用工具管理记忆，不需要额外的”自动提取” agent。

### 5. 示例数据种子 (Sample Data Seeding)
- **实现**：`SeedManager`（`data/SeedManager.kt`）在数据库为空且未播种过（SharedPreferences `seed_done_v1`）时插入几条硬编码示例任务（”Have Breakfast” 等）。
- **仅 debug 生效**：`TodoRepository` 的 `seedManager` 是可选的构造参数（默认 `null` = 不播种）。生产装配（`MainViewModel.Factory`）只在 `BuildConfig.DEBUG` 为真时才传入真实 `SeedManager`；release 包和任何手动构造 `TodoRepository(...)` 不传参的场景（比如 androidTest）都不会触发播种，避免真机上跑测试时误写生产数据库。
- **改示例数据内容后怎么让老安装也刷新**：`SEED_DONE_KEY` 常量带版本号后缀（`seed_done_v1`），改了 `seedWithSampleData()` 里的内容后把 key 后缀改成 `_v2` 即可让已播种过的老安装重新播种一次。
- **不要做成”每次安装自动清空重来”**：debug 包通常被当成半持久的日常开发环境使用，自动清空会丢掉开发者手动攒的测试数据；如果需要真正的”全新状态”应该走 `adb uninstall`/清除应用数据这类开发者主动操作。
- **手动重置入口**：Settings 页底部有一个仅 `BuildConfig.DEBUG` 下可见的”开发者选项”卡片（`SettingsScreen.kt` 里的 `DeveloperSection`），点击”重置示例数据”会弹二次确认，确认后调用 `MainViewModel.resetSampleData()` → `TodoRepository.resetSampleData()` → `SeedManager.resetAndReseed()`：删光所有任务、清掉播种标记、重新插入示例数据。
