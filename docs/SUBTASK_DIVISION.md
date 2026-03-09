# 子任务划分模块文档

## 概述

子任务划分模块是一个专用的AI驱动功能，能够智能地将复杂任务分解为更小、更易管理的子任务。该模块集成了OpenAI API，提供三种不同的划分策略，支持用户自定义配置。

## 功能特性

### 🤖 AI智能划分
- 基于任务标题和内容进行智能分析
- 支持上下文理解和任务优先级继承
- 自动生成合理的子任务执行顺序

### 📋 多种划分策略
- **详细划分 (DETAILED)**: 生成更多细粒度的子任务，适合复杂项目
- **平衡划分 (BALANCED)**: 适中的子任务数量和粒度，推荐选择
- **简化划分 (SIMPLIFIED)**: 生成较少的高层次子任务，适合简单任务

### ⚙️ 灵活配置
- 可配置最大子任务数量 (1-10个)
- 支持AI/模板两种生成模式
- 可设置优先级继承和自动创建选项

## 架构设计

### 核心组件

```
assistant/subtask/
├── SubtaskDivider.kt              # 核心接口
├── AISubtaskDivider.kt            # AI实现
├── MockSubtaskDivider.kt          # 模拟实现
├── SubtaskDivisionService.kt      # 服务层
├── SubtaskDivisionConfig.kt       # 配置管理
├── SubtaskDivisionRequest.kt      # 请求模型
└── SubtaskDivisionResponse.kt     # 响应模型
```

### 数据流

```
用户点击AI划分按钮
    ↓
TaskDetailSheet显示策略选择对话框
    ↓
MainScreen触发SubtaskDivisionEvent
    ↓
MainViewModel调用SubtaskDivisionService
    ↓
AISubtaskDivider调用OpenAI API
    ↓
解析响应并创建子任务到数据库
    ↓
UI通过Room Flow自动更新
```

## 使用方法

### 1. 在任务详情页面使用

1. 打开任何任务的详情页面
2. 点击底部工具栏中的 **AI划分** 按钮 (⭐图标)
3. 在弹出的对话框中选择划分策略：
   - 详细划分：适合复杂项目
   - 平衡划分：推荐的默认选择
   - 简化划分：适合简单任务
4. 选择是否使用AI智能划分
5. 点击"开始划分"按钮

### 2. 编程方式使用

```kotlin
// 创建子任务划分服务
val subtaskDivisionService = SubtaskDivisionService(
    assistantClient = assistantClient,
    todoRepository = todoRepository
)

// 生成子任务建议
val result = subtaskDivisionService.generateSubtaskSuggestions(
    task = parentTask,
    strategy = DivisionStrategy.BALANCED,
    maxSubtasks = 5,
    useAI = true
)

result.fold(
    onSuccess = { response ->
        // 处理成功结果
        println("生成了 ${response.subtasks.size} 个子任务建议")
    },
    onFailure = { error ->
        // 处理错误
        println("划分失败: ${error.message}")
    }
)
```

### 3. 批量划分

```kotlin
// 批量划分多个任务
val result = subtaskDivisionService.batchDivideAndCreate(
    tasks = selectedTasks,
    strategy = DivisionStrategy.DETAILED,
    useAI = true
)
```

## 配置选项

### 全局配置

```kotlin
// 更新默认配置
SubtaskDivisionConfigManager.updateConfig(
    SubtaskDivisionConfig(
        defaultStrategy = DivisionStrategy.BALANCED,
        defaultMaxSubtasks = 5,
        enableDependencyAnalysis = true,
        enablePriorityInheritance = true,
        autoCreateSubtasks = false,
        requireUserConfirmation = true
    )
)
```

### 策略配置

```kotlin
// 单独更新策略
SubtaskDivisionConfigManager.updateStrategy(DivisionStrategy.DETAILED)

// 更新最大子任务数
SubtaskDivisionConfigManager.updateMaxSubtasks(8)

// 切换自动创建模式
SubtaskDivisionConfigManager.toggleAutoCreate(true)
```

## API参考

### SubtaskDivisionRequest

```kotlin
data class SubtaskDivisionRequest(
    val taskTitle: String,           // 任务标题
    val taskContent: String? = null, // 任务内容
    val taskPriority: Priority = Priority.DEFAULT,
    val maxSubtasks: Int = 5,        // 最大子任务数
    val strategy: DivisionStrategy = DivisionStrategy.BALANCED,
    val context: String? = null      // 额外上下文
)
```

### SubtaskDivisionResponse

```kotlin
data class SubtaskDivisionResponse(
    val originalTask: String,        // 原始任务标题
    val subtasks: List<SubtaskSuggestion>, // 子任务建议列表
    val reasoning: String? = null    // AI分解思路说明
)

data class SubtaskSuggestion(
    val title: String,               // 子任务标题
    val content: String? = null,     // 子任务内容
    val priority: Priority = Priority.DEFAULT,
    val estimatedOrder: Int,         // 建议执行顺序
    val dependencies: List<Int> = emptyList() // 依赖关系
)
```

### DivisionStrategy

```kotlin
enum class DivisionStrategy {
    DETAILED,    // 详细划分
    BALANCED,    // 平衡划分
    SIMPLIFIED   // 简化划分
}
```

## 示例场景

### 场景1: 软件开发项目

**原始任务**: "开发用户登录功能"

**AI划分结果** (平衡策略):
1. 设计用户登录界面UI
2. 实现用户认证逻辑
3. 集成数据库用户验证
4. 添加错误处理和验证
5. 编写单元测试

### 场景2: 学习计划

**原始任务**: "学习Kotlin编程语言"

**AI划分结果** (详细策略):
1. 了解Kotlin基础语法
2. 学习面向对象编程概念
3. 掌握函数式编程特性
4. 学习协程和并发编程
5. 实践Android开发应用
6. 完成综合项目练习

### 场景3: 生活任务

**原始任务**: "准备生日聚会"

**AI划分结果** (简化策略):
1. 制定聚会计划和预算
2. 采购食物和装饰用品
3. 邀请客人并安排场地

## 最佳实践

### 1. 选择合适的策略
- **复杂技术项目**: 使用详细划分策略
- **日常工作任务**: 使用平衡划分策略  
- **简单生活事务**: 使用简化划分策略

### 2. 提供充分的上下文
- 在任务标题中包含关键信息
- 在任务内容中添加具体要求
- 利用上下文参数提供额外信息

### 3. 合理设置子任务数量
- 简单任务: 2-3个子任务
- 中等复杂度: 4-6个子任务
- 复杂项目: 7-10个子任务

### 4. 优先级管理
- 启用优先级继承确保一致性
- 根据需要手动调整子任务优先级
- 考虑任务间的依赖关系

## 故障排除

### 常见问题

**Q: AI划分功能不可用**
A: 检查OpenAI API密钥配置，确保网络连接正常

**Q: 生成的子任务不合理**
A: 尝试不同的划分策略，或在任务内容中提供更多上下文信息

**Q: 子任务创建失败**
A: 检查数据库连接，确保父任务存在且有效

**Q: 划分速度较慢**
A: AI处理需要时间，可以先使用模拟模式进行测试

### 调试信息

启用调试日志查看详细信息：
```kotlin
Log.d("SubtaskDivisionService", "Generated ${response.subtasks.size} subtask suggestions")
```

## 扩展开发

### 添加新的划分策略

1. 在`DivisionStrategy`枚举中添加新策略
2. 在`AISubtaskDivider`中更新提示词逻辑
3. 在UI组件中添加策略描述

### 自定义AI提示词

修改`AISubtaskDivider.buildPrompt()`方法来自定义AI行为：

```kotlin
private fun buildPrompt(request: SubtaskDivisionRequest): String {
    // 自定义提示词逻辑
    return customPrompt
}
```

### 集成其他AI服务

实现`SubtaskDivider`接口来支持其他AI服务：

```kotlin
class CustomAISubtaskDivider : SubtaskDivider {
    override suspend fun divideTask(request: SubtaskDivisionRequest): Result<SubtaskDivisionResponse> {
        // 自定义AI服务实现
    }
}
```

## 版本历史

- **v1.0.0**: 初始版本，支持基本的AI子任务划分功能
- 支持三种划分策略
- 集成TaskDetailSheet用户界面
- 提供完整的配置管理系统

---

*最后更新: 2025-08-27*
