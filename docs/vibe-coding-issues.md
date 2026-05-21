# Vibe Coding 踩坑记录：AI 相关问题

> 项目：TaskFlow AI，一个基于 LLM 的 ReAct 模式自主任务执行系统
>
> 日期：2026-05-22

---

## 问题 1：LLM 不遵守工具名，返回编造的名字

### 现象

DeepSeek 返回的 `selected_tool` 字段与代码注册的工具名不一致：

```
代码注册: weather
AI 返回: get_weather / query_weather / weather_query / get_current_weather
结果: Tool not found: get_weather
```

有时 7 个步骤全部失败，没有一次用到正确的 `weather`。

### 原因

这个项目**没有使用原生 function calling**，而是把工具列表作为纯文本嵌入 prompt，LLM 根据语义"猜测"工具名返回。DeepSeek 的 function calling 能力不如 OpenAI，在文本 prompt 模式下工具名是 LLM 自由发挥的文本字段，不是结构化的函数调用。

### 修复

1. **ToolManager 增加模糊匹配**：去掉下划线、统一小写后做包含匹配，`get_weather` → `weather` 能命中
2. **Action Prompt 显式列出工具名**：每步决策时把可用工具名列表传给 LLM，prompt 中强调"必须使用上述工具名之一"

### 教训

文本 prompt 模式下的工具调用本质上是"让 LLM 填 JSON 表单"，工具名是自由文本，LLM 可能不按预期填写。**模糊匹配作为容错层**是必需的，不能假设 LLM 会严格遵守格式。

**如果用 OpenAI 的原生 function calling，这个问题不会出现**——因为工具定义是结构化传入的，模型 API 层面就保证了返回的工具名和定义一致。

---

## 问题 2：工具失败后，LLM 放弃重试直接结束

### 现象

天气工具调用失败（401 或工具不存在），AI 只执行了 1 步就标记 COMPLETED，最终结果是"任务执行完成"。

```
1. get_weather → 工具不存在
→ Reflection: is_sufficient: true
→ Task completed. 结束。
```

### 原因

ReAct 循环中，每步执行完会走 Reflection 环节，问 LLM"这个结果是否充分回答了原始任务？"DeepSeek 看到工具调用返回了错误，认为"这任务我做不了"，直接返回 `is_sufficient: true`，系统收到后就退出了循环。

LLM 没有"重试"或"换工具再试"的意识，它把它看到的结果当成了最终状态。

### 修复

**步骤失败时直接跳过 Reflection，进入下一轮循环。** 不让 LLM 判断是否结束——失败了就该重试，不需要 LLM 批准。

```java
// 之前：无论成功失败都反思
step.execute();
reflect();  // LLM 说 "够了" → 结束

// 之后：失败就跳过反思，继续循环
if (step.isSuccess()) {
    reflect();  // 只有成功时才问 LLM "够了吗"
}
```

### 教训

LLM 在失败面前容易"摆烂"。**失败重试应该由代码逻辑控制，不要让 LLM 参与决策**。LLM 适合判断"任务完成度"，不适合判断"要不要继续努力"。

---

## 问题 3：反思文本被当成最终结果返回给用户

### 现象

天气查询成功拿到数据，但用户看到的最终结果是一句元分析：

```
最终结果: 原始任务询问北京今天的天气，结果提供了北京（2026年5月22日凌晨）
的详细天气数据，包括温度、天气状况、湿度、风速等，完整地回答了问题。
```

用户期望的是"北京当前多云，21.8°C，湿度 58%……"，得到的却是对执行过程的评价。

### 原因

代码中 `Reflection → is_sufficient: true` 时直接调用：

```java
ctx.markCompleted(reflection.path("reasoning").asText("任务已完成"));
```

反思的 `reasoning` 是系统内部元分析文本（给开发者的），不是给用户看的答案。这行代码把元分析写入了 `finalResult`，后面的 Synthesis 阶段发现 `finalResult != null` 就跳过了——用户最终看到的是系统日志而不是天气报告。

**这是典型的"把系统 prompt 的回复当成了用户 prompt 的回复"。** 反思 prompt 问的是"这个结果充分吗？"LLM 回答的是评估意见；Synthesis prompt 问的是"给用户一个答案"，LLM 才会输出用户友好的内容。两段对话的目的不同，不能混用。

### 修复

1. **分离"标记完成"和"设置结果"**：反思判定充分时只标记状态，不写入 finalResult
2. **强制走 Synthesis**：不管反思结果如何，最后由 Synthesis 生成用户友好的答案
3. **Synthesis Prompt 改进**：传入全部步骤的完整数据，明确要求格式化输出

### 教训

多轮 LLM 调用的项目中，每轮调用的输出用途不同——有的给系统判断（反思），有的给用户展示（合成）。**给系统看的和给用户看的不能混在一起**，用不同的字段存储，让最后一步始终负责用户输出。

---

## 问题 4：DeepSeek 模型名不匹配

### 现象

配置了 DeepSeek 的 API Key，但调用时报错：

```
Unrecognized token 'Error': was expecting (JSON String, Number, Array, Object...)
at [Source: (String)"Error: API returned status 400"
```

### 原因

`application.yml` 中 `model` 写的是 `gpt-4o`（OpenAI 的模型名），但实际调用的是 DeepSeek 的 API。DeepSeek 不认识 `gpt-4o` 这个模型名，返回了 400 错误。LLMClient 把 non-JSON 错误信息当 JSON 解析，导致连锁报错。

### 修复

改为 DeepSeek 的模型名，如 `deepseek-chat` 或 `deepseek-v4-flash`。

### 教训

API Base URL 和 Model Name 是配套的，换了 provider 就必须换 model。**provider 和 model 可以做成联动校验**，比如检测到 base URL 包含 `deepseek` 时自动校验 model 是否在 DeepSeek 的模型列表中。

---

## 总结：这 4 个问题的共性

| 问题 | 本质 |
|------|------|
| 工具名不匹配 | **过度信任 LLM 的输出规范性和精确性** |
| 失败后放弃 | **让 LLM 参与不该它做的控制流决策** |
| 反思当结果 | **混淆了不同轮次 LLM 对话的职责边界** |
| 模型名不匹配 | **配置之间缺少一致性校验** |

核心教训：在 ReAct 模式中，**LLM 适合做"内容生成"和"语义判断"，不适合做"精确匹配"和"流程控制"**。精确匹配交给代码（模糊搜索 / 枚举约束），流程控制也交给代码（步骤失败 → 重试 → 不经过 LLM 审批）。
