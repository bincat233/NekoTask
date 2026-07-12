# 子任务拆分模块

本文档记录当前实现，不描述旧的 `assistantClient` / strict JSON action client 流程。

## 目标

子任务拆分把一个较大的 `Task` 转换成多个可执行子任务，并写回 Room。它服务于低认知负担的核心目标：用户不必先手动规划完整步骤，也能从一个更小的下一步开始。

## 当前数据流

```text
TaskDetailSheet action
    -> SubtaskDivisionEvent.CreateFromSuggestions
    -> SubtaskDivisionCoordinator
    -> SubtaskDivisionService
    -> AISubtaskDivider or MockSubtaskDivider
    -> TodoRepository.addTask(parentId = parentTask.id)
    -> Room Flow refreshes UI
```

详情页触发拆分时，`MainScreen` 会从 `SettingsState` 读取：

- `aiDivisionStrategy`
- `maxSubtasks`
- `useAI`

这些值随事件传入 `SubtaskDivisionCoordinator`。Coordinator 会从共享 `LlmRuntime` 读取当前 `PromptExecutor` 和 `LLModel`，因此 Settings 中的 provider/model/key 变更会影响后续子任务拆分。

## 核心组件

- `SubtaskDivisionCoordinator`：UI 事件到拆分服务的编排层；负责 loading 标记和任务查找。
- `SubtaskDivisionService`：组合 divider 和 `TodoRepository`；可只生成建议，也可直接创建子任务。
- `AISubtaskDivider`：Koog agent 实现，要求模型通过 tool call 返回结构化子任务结果。
- `MockSubtaskDivider`：非 AI fallback，按策略生成模板化子任务。
- `SubtaskDivisionRequest` / `SubtaskDivisionResponse`：模块边界数据模型。
- `DivisionStrategy`：`DETAILED`、`BALANCED`、`SIMPLIFIED`。

## 行为说明

- 默认策略来自 settings，默认最大子任务数为 5。
- `useAI = true` 时使用 `AISubtaskDivider`；`useAI = false` 时使用 `MockSubtaskDivider`。
- `CreateFromSuggestions` 当前会 `forceCreate = true`，即生成后直接写入数据库。
- 创建子任务使用 `TodoRepository.addTask(...)`，实际顺序由 Room ordering 逻辑追加计算。
- `TodoRepository.addTask(...)` 返回数据库生成的真实 id，`createdTasks` 会带上这些 id。
- UI 通过 Room Flow 自动刷新；拆分结果本身目前不作为独立 UI 状态展示。

## 已知限制

- `GenerateSuggestions` 只写日志，不显示建议确认 UI。
- 依赖关系字段会保存在响应模型里，但当前创建任务时不会持久化依赖关系。

## 维护建议

- 若要做“生成建议 -> 用户确认 -> 创建”，先给 `SubtaskDivisionCoordinator` 增加专门 state，而不是把建议塞进 `TaskDetailState` 的零散字段。
- 若 UI 需要展示建议确认流程，先定义 `SubtaskDivisionCoordinator` 的状态和事件，不要让 `TaskDetailSheet` 直接持有拆分业务状态。
- 若新增 provider 或模型配置，确认 `LlmRuntime.buildExecutor()` 和 `getCurrentModel()` 对子任务拆分仍可用。
- 不要重新引入旧 `assistantClient` 构造方式；当前 AI runtime 统一走 `ChatAgent` / `LlmRuntime` seams。
