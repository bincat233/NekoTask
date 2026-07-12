# AI 任务 / 记忆工具协议

本文档记录当前实现,不描述迭代历史。工具通过 Koog 的 `ai.koog.agents.core.tools.reflect.ToolSet`
反射机制暴露给 LLM——每个 `@Tool` 方法的参数和 `@LLMDescription` 会被 Koog 转成 JSON Schema,
模型据此发起 function call。

## 组件

- `TaskToolSet`:任务 CRUD、批量操作、checklist 子任务树。持有 `TodoRepository`。
- `MemoryToolSet`:长期记忆 CRUD。持有 `LongTermMemoryRepository`。
- `TodoAgent`:把两个 ToolSet 注册进 `ToolRegistry`,`baseSystemPrompt` 里写高层调用规则,
  细节规则留在各工具的 `@LLMDescription` 里。

## 任务工具清单(`TaskToolSet`)

| 工具 | 用途 | 备注 |
|---|---|---|
| `add_task` | 新增单个任务或单个子任务 | `content` 命中 `looksLikeMultiItemChecklist` 会拒绝,引导模型改用批量/子任务工具 |
| `add_task_with_subtasks` | 一次性创建父任务 + 一组有序子任务 | 清单/计划/打包列表类请求走这里 |
| `add_subtasks` | 给已有父任务批量加子任务 | 需要 `CURRENT_TODO_STATE` 里已有的父任务 ID |
| `add_tasks` | 批量新增多个**互不相关**的独立任务(上限 25) | 不支持 `parentId`/`orderInParent`——要挂父任务下用 `add_subtasks`。返回 `BatchResult` JSON,见下 |
| `update_task` | 修改标题/备注/截止时间/优先级/父任务/顺序 | `content`/`dueAt` 传字面量 `"remove"` 清空该字段;`detachFromParent=true` 把任务摘成顶层(若同时传了 `parentId` 以 `parentId` 为准) |
| `delete_task` | 按 ID 删除任务 | 级联删除子任务(`repository.deleteTaskRecursively`,跟人工 UI 删除入口一致),返回值包含删除总数,比如 `"ok: deleted task and 8 subtask(s)"` |
| `get_task` | 按 ID 拿任务完整详情,含 `content` | `CURRENT_TODO_STATE` 快照不含 `content`,模型要引用/追加已有备注前应该先调这个,返回 JSON(`TaskDetail`) |
| `complete_task` | 标记单个任务完成 | |
| `uncomplete_task` | 把已完成任务标回未完成 | 跟 `complete_task` 对称,调用同一个 `repository.toggleTaskStatus(id, done)` |
| `complete_tasks` | 批量完成多个任务(上限 25) | 返回 `BatchResult` JSON |

## 记忆工具清单(`MemoryToolSet`)

`add_memory` / `update_memory` / `delete_memory` / `list_memories`——CRUD 长期记忆,category 限定在
`general/preferences/work_habits/project_info/personal/context` 六类,不认识的归到 `general`。

## 批量操作:`BatchResult`

`add_tasks`/`complete_tasks` 返回结构化 JSON(单条工具仍然是 `"ok: ..."`/`"error: ..."` 纯文本,
两种风格按"标量 vs 列表结果"分工,不是不一致):

```json
{
  "successCount": 3,
  "failureCount": 1,
  "ids": [101, 102, 104],
  "failures": [{ "index": 3, "error": "title required" }]
}
```

- `ids`:成功项按处理顺序收集到的任务 ID(不保留失败项的位置)。
- `failures[].index`:失败项在**输入数组里的 1-based 下标**,不是标题,因为标题可能重复。
- 每一项独立走 `repository` 单条方法(比如 `add_tasks` 内部循环调 `repository.addTask(...)`),
  某一项失败不影响其它项已经写入的数据——模型可以只针对 `failures` 里的下标重试,不用重发整批。
- 之所以在 `TaskToolSet` 层循环、而不是在 `TodoRepository` 里包一个批量事务方法:现在的
  `repository.addTask()`/`toggleTaskStatus()` 都是单条方法,以后若引入联网同步后端,这些方法的
  签名不用变,循环 + 收集成败的代码也不用改;反过来如果现在做成"一个本地事务要么全成功要么全
  回滚",引入网络请求后事务语义站不住,还得推倒重做。

## `update_task` 的清空 / 摘除父子关系语义

- `content`/`dueAt` 用 Kotlin 默认参数 `null` 表示"不改",没法表示"清空"——所以用字面量
  `"remove"` 作为 sentinel:传 `"remove"` 会先各自调用 `repository.updateContent(id, null, ...)` /
  `repository.updateDueAt(id, null, ...)` 单独清空,再执行组合的 `repository.updateTask(...)`
  处理其余字段。`looksLikeMultiItemChecklist` 校验会跳过字面量 `"remove"`,避免误判。
- `detachFromParent: Boolean = false`:传 `true` 复用 `repository.moveTaskToParent(id, null, orderInParent)`
  把任务摘成顶层任务。若同时传了非空 `parentId`,以 `parentId` 为准,忽略 `detachFromParent`。

## 已知后续工作

1. **内部控制流坏味道**:`TaskToolSet` 里多处用 `.startsWith("ok")` 判断某次内部子调用
   (`moveTaskToParent`/`reorder` 等)是否成功,是字符串判断而不是类型判断。计划是把这些内部
   调用收敛成 Kotlin `Result`/`sealed class`,只在每个 `@Tool` 方法的最后一行才序列化成对外的
   字符串/JSON——这样"内部怎么判断成功失败"和"对外协议长什么样"解耦,以后想换协议格式不用
   连内部逻辑一起翻。这次批量工具的改动没有顺带做这个重构,单独记在这里。
2. **循环任务(RRULE)**:`Task`/`TaskEntity` 目前没有 `repeatFlag` 字段,以后加是纯 additive
   migration。到时候新增一个独立的 `reschedule_task` 工具处理"下一次日期"语义,不要复用
   `update_task` 的 `dueAt`——否则会重蹈 Todoist 早期"改 dueString 顺带把循环任务变成一次性任务"
   的坑(它们后来专门拆出 `reschedule-tasks` 来解决)。
3. **以后接入联网后端时需要重新评估的项**——这几项这轮对比里确认过"现在不是缺口",但结论
   建立在"本地单用户、数据量小"这个前提上,接后端时前提可能不成立,这里存档当时的判断依据:
   - **批量 `update_tasks`**:Todoist MCP 有 `update-tasks` 处理批量改字段,我们只有单条
     `update_task`。没做是因为"一次改多个任务的不同字段"是低频对话模式,不像批量新增/批量
     完成那么常见。以后如果后端支持协作、出现真实批量改字段的用例,直接照搬 `add_tasks`/
     `complete_tasks` 已经验证过的 `BatchResult` 模式,不用重新设计。
   - **search/filter 类工具**:Todoist 的 `find-tasks`/`filter_tasks` + 游标分页,TickTick 的
     `list_undone_tasks_by_date`(14 天窗口上限)/`search`,本质是"数据在远程、只能查子集"的
     产物。我们现在每轮把全量 `CURRENT_TODO_STATE` 塞给模型,等价于免费拿到全量搜索效果——
     这个假设只在数据量小、单用户本地存储时成立。一旦接联网后端(多设备同步、数据量可能变大),
     需要重新评估:要么继续全量快照但加分页/摘要,要么引入类似 `find_tasks`/`filter_tasks` 的
     查询工具。到时候直接参考 Todoist 的过滤字段设计(`startDate/endDate/projectIds/priority/
     tag/kind/status`)和 TickTick 的时间窗口预设(`today/tomorrow/next7day` 等)。
   - **labels/reminders/projects/comments**:两份参考都有,我们的 `Task`/`TaskEntity` 领域模型
     目前没有对应字段,这是模型层面的新功能而不是协议层面的缺口。以后要加,协议设计上可以直接
     抄这次已经验证过的两个模式:字符串 sentinel(清空用字面量,参考 `update_task` 的 `"remove"`)
     和枚举而非数字的优先级/状态字段(避免数字歧义)。
