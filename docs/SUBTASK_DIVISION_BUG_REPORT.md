# 任务拆分按钮 Bug 诊断报告

> 日期: 2026-07-13 | 状态: 待修复

## 现象

点击任务详情页的 AI 拆分按钮（AutoAwesome 图标），按钮短暂显示 loading 后恢复，无任何子任务生成，无用户可见错误提示。

## 根因定位

**LLM 从未调用 `divide_task` 工具。** Agent 运行 3-5 个迭代（每次约 3 秒 LLM round-trip），每次都抛出 `AIAgentMaxNumberOfIterationsReachedException`，`toolSet.lastResult` 始终为 null。

日志证据（过滤 `DIAG`）：
```
[DIAG] AISubtaskDivider.divideTask: running agent...
(~10-28s later)
[DIAG] createSubtasksFromAI FAILED: AIAgentMaxNumberOfIterationsReachedException
```

## 已试错的路径

全部指向同一失败——工具从未被 LLM 调用：

| 尝试 | 结果 |
|---|---|
| `maxAgentIterations`: 1 → 3 → 5 | ❌ 失败 |
| `ToolChoice.Required` → 移除 | ❌ 失败 |
| `AIAgentConfig` + prompt DSL → `AIAgent(llmModel, strategy, ...)` | ❌ 失败 |
| `DivideTaskToolSet`: `inner class` → 嵌套 `class` → 独立顶层类 | ❌ 失败 |
| Library `singleRunStrategy()` → 默认构造（无显式 strategy） | ❌ 失败 |
| 简化 `@LLMDescription` 参数注解 | ❌ 失败 |

## Chat 工具正常工作的对比

Chat 的 `TaskToolSet` / `MemoryToolSet` 使用**完全相同**的 `AIAgent(llmModel, strategy, toolRegistry, systemPrompt, maxIterations)` 模式，工具调用正常。已将所有代码对齐到与 chat 一致的形态。

## 关键线索

1. Koog 示例中存在两种工具注册方式：
   - **`ToolSet` + 反射**: `tools(weatherTools)` — `WeatherTools` extends `ToolSet`, `@Tool` 注解方法
   - **`Tool<Args, Result>` 基类**: `tool(CalculatorTool.PlusTool)` — 显式声明 name/description/argsType/resultType
   
2. Trip-planner 示例中 `WeatherTools : ToolSet` 工作正常。但 `DivideTaskToolSet : ToolSet` 不工作，即使代码结构已完全对齐。

3. `Agent.kt` 中的 `createSimplePlannerAgent` **不使用** `singleRunStrategy()`（用默认构造），工具正常工作。

## 建议下一步

1. **将 `divide_task` 改为 `Tool<Args, Result>` 基类实现**（而非 `ToolSet` 反射），参考 `CalculatorTool` 示例
2. **深入 Koog 源码**排查 `ToolRegistry.tools(ToolSet)` 反射为何对 `DivideTaskToolSet` 失效
3. 当前 `[DIAG]` 日志可用于验证修复

## 涉及文件

- `AISubtaskDivider.kt` — 核心调用逻辑
- `DivideTaskToolSet.kt` (新建) — 工具定义（top-level class）
- `SubtaskDivisionCoordinator.kt` — 协调器（含 DIAG 日志）
- `SubtaskDivisionService.kt` — 服务层
- `SubtaskDivisionState.kt` — State（新增 errorMessage 字段）
- `MainViewModel.kt` / `MainScreen.kt` / `TaskDetailSheet.kt` — UI 层错误反馈
- `MainScreenIntent.kt` — 新增 DismissSubtaskDivisionError intent
