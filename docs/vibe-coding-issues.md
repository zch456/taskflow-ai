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

---

## 问题 5：ReAct 循环无限交替 + API 重复调用导致超时

### 现象

任务"查询太原天气并推荐散步地点"执行了 9 步、耗时 70 秒后 TIMEOUT：

```
步骤 1: weather  → 太原天气（23°C，晴天）
步骤 2: search  → 太原散步推荐（迎泽公园、汾河公园...）
步骤 3: weather  → 太原天气（23°C，晴天）  ← 重复！和步骤1完全相同
步骤 4: search  → 太原散步推荐            ← 重复！和步骤2完全相同
步骤 5: weather  → 太原天气
步骤 6: search  → 太原散步推荐
步骤 7: weather  → ...
步骤 8: search  → ...
步骤 9: weather  → ...
→ TIMEOUT（70s）
```

LLM 被困在 weather → search → weather → search 的无限交替循环中。同一个 Weather API 调了 5 次，同一个 Search API 调了 4 次，结果完全一样。

### 根因分析

这是三个问题的叠加：

**① Prompt 导致 LLM "短期失忆"（主因）**

`buildActionPrompt` 和 `buildReflectPrompt` 调用 `summarizeHistory(ctx)`，但该方法只返回**最后一步**的结果。LLM 每一步只看到最新的工具输出，不知道之前已经拿过什么数据：

```
步骤 1 完成后 → LLM sees: "天气 23°C 晴天"
步骤 2 完成后 → LLM sees: "迎泽公园、汾河公园..."  （天气数据丢失！）
步骤 3 → LLM: "我有散步推荐但缺天气，再查一次"
步骤 4 → LLM: "我有天气但缺散步推荐，再查一次"  （永恒循环）
```

**② Redis 缓存注入时序 bug**

`TaskAgentService` 用 `@Autowired(required = false)` 字段注入 `RedisCacheService`。Spring 先调构造函数、后注入字段，构造函数里传 `cacheService` 时它永远是 `null`。缓存从未生效，重复 API 调用的结果没有被拦截。

**③ 缺少代码级兜底**

即使 Prompt 修好了，DeepSeek V4 Flash 这类小模型在 ReAct 循环中本身就不稳定。循环终止完全依赖 LLM 返回 `is_sufficient: true`，没有任何代码层面的硬性保护。

### 修复

**修复 1：Prompt 展示全量历史**

`summarizeHistory()` → `buildFullHistory()`，列出所有已执行步骤的结果。LLM 现在看到的是：

```
步骤 1 [weather]: 太原，23°C，晴天
步骤 2 [search]: 迎泽公园、汾河公园...
→ LLM 判断: 两个信息都有了，is_sufficient=true，结束
```

**修复 2：Redis 缓存改为构造函数注入**

```java
// 改前：字段注入，构造函数时 cacheService 永远是 null
@Autowired(required = false)
private RedisCacheService cacheService;

// 改后：构造函数参数注入
public TaskAgentService(..., @Autowired(required = false) RedisCacheService cacheService) {
    this.cacheService = cacheService;  // 此时已注入
}
```

**修复 3：增加代码级安全阀**

```java
// 同一工具被调用 2 次以上，直接强制终止循环
if (countToolCalls(ctx, toolName) >= 2) {
    ctx.markCompleted("已完成必要的工具调用");
    break;
}
```

**修复 4：超时从 30s 放宽到 60s**

正常 1 次 weather + 1 次 search + LLM 合成大约需要 30 秒，30s 太紧。

### 修复前后对比

| 指标 | 修复前 | 修复后 |
|------|--------|--------|
| 总耗时 | 70,368ms | **29,862ms**（-57%） |
| 执行步骤数 | 9 步 | **2 步** |
| Weather API 调用 | 5 次 | **1 次** |
| Search API 调用 | 4 次 | **1 次** |
| LLM 调用 | ~18 次（9 步 × 2 轮） | **~5 次**（plan + action + reflect + synthesis × 1） |
| 最终状态 | TIMEOUT | **COMPLETED** |
| 结果质量 | 同质化（数据一样） | 质量相同，速度更快 |

**每次同类任务节省约 8 次 API 调用。** 按 DeepSeek V4 Flash 和 Tavily API 的计费标准，每次任务从约 $0.15 降到约 $0.03。

### 教训

1. **不要让 LLM "记住"上下文——Prompt 里要有全量历史。** LLM 的视界仅限于当前 prompt 里的内容，你不在 prompt 里写进去的东西，LLM 就不知道。

2. **反复调用同一个工具是 LLM 的常见 failure mode。** 代码层面必须检测重复调用并强制执行停止策略，不要迷信"LLM 会自己判断该停"。

3. **Spring `@Autowired(required = false)` 字段注入有时序陷阱。** 要在构造函数里使用可选依赖，必须放在构造函数参数上，不能放在字段上。

4. **ReAct 循环需要三条防线：** (a) 好的 Prompt（让 LLM 有足够上下文做对决策），(b) 代码级安全阀（LLM 搞不定时强行拉起），(c) 合理的超时（给正常流程留足空间但不无限等待）。

---

## 问题 6：Search API 认证方式不匹配——GET + query param 调 Tavily 返回 401

### 现象

搜索工具配置了 Tavily API Key 和 Base URL 后，每次调用返回：

```
搜索API调用失败: HTTP 401
```

`curl` 直接调 Tavily 却正常返回 200，确认 Key 有效。

### 原因

AI 生成的 `SearchTool.java` 用 GET 方式调用搜索 API，API Key 作为 query 参数 `?key=xxx` 传递：

```java
// AI 生成的代码（GET + query param）
HttpUrl url = HttpUrl.parse(apiBaseUrl);
url = url.newBuilder()
        .addQueryParameter("q", query)
        .addQueryParameter("key", apiKey)   // ← Tavily 不认这个
        .build();
Request request = new Request.Builder().url(url).get().build();
```

但 **Tavily API 要求 POST + JSON body**，API Key 放在 `api_key` 字段中：

```json
POST https://api.tavily.com/search
{"api_key": "tvly-xxx", "query": "...", "max_results": 5}
```

不同搜索服务的认证方式完全不同：

| 服务 | 方法 | Key 位置 |
|------|------|---------|
| Tavily | POST | JSON body `api_key` |
| SerpAPI | GET | query param `api_key` |
| Google CSE | GET | query param `key` + `cx` |
| Bing | GET | Header `Ocp-Apim-Subscription-Key` |

AI 生成代码时没有区分具体服务的认证方式，默认用了最通用的 GET + `?key=` 模式，恰好和 Tavily 不兼容。

### 修复

改为 Tavily 标准的 POST + JSON body：

```java
Map<String, Object> body = new LinkedHashMap<>();
body.put("api_key", apiKey);
body.put("query", query);
body.put("search_depth", "basic");
body.put("max_results", count);

Request request = new Request.Builder()
        .url(apiBaseUrl)
        .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
        .build();
```

### 教训

**AI 不会主动查 API 文档。** 它用的是一个通用的"把 key 放 query param"的模式，不区分具体服务。集成任何第三方 API 时，必须自己对着文档验证：HTTP 方法（GET/POST）、认证方式（query param / header / body）、参数名（`key` vs `api_key` vs `apikey`）是否与代码一致。

---

## 问题 7：数据库 VARCHAR(5000) 溢出——LLM 工具输出远超字段长度

### 现象

搜索任务执行时报错：

```
ERROR: value too long for type character varying(5000)
insert into execution_steps (..., output_result, ...) values (..., ?, ...)
```

搜索 API 返回的 JSON 结果包含了长篇网页摘要，轻松超过 5000 字符的字段限制。

### 原因

JPA Entity 中 AI 生成的长度定义过于保守：

```java
// ExecutionStepEntity.java
@Column(length = 5000)   // ← 搜索结果可达 10000+ 字符
private String outputResult;

@Column(length = 2000)   // ← LLM 参数 JSON 也可能很大
private String inputParams;

// TaskExecutionEntity.java
@Column(length = 5000)   // ← LLM 合成结果可达数千字
private String finalResult;
```

这是 AI 代码的典型问题：**用常规 Web 应用的数据建模经验（VARCHAR 5000 够了）来处理 LLM 工具的输出。** LLM 调用外部 API 返回的 JSON（如 Tavily 搜索结果的 `content` 字段含有完整网页段落）、LLM 自身的合成回答，都可能远超传统 VARCHAR。

### 修复

关键字段全部改为 PostgreSQL 的 TEXT 类型（无长度限制）：

```java
@Column(columnDefinition = "TEXT")
private String outputResult;

@Column(columnDefinition = "TEXT")
private String inputParams;

@Column(columnDefinition = "TEXT")
private String finalResult;

@Column(columnDefinition = "TEXT")
private String errorMessage;
```

同时需要手动 ALTER TABLE（`ddl-auto: update` 不修改已有列）：

```sql
ALTER TABLE execution_steps ALTER COLUMN output_result TYPE TEXT;
ALTER TABLE task_executions ALTER COLUMN final_result TYPE TEXT;
```

### 教训

**LLM 项目的数据字段不应该用 VARCHAR 限制长度。** 工具返回的 JSON、LLM 生成的文本、错误堆栈信息——这些都可能在运行时远超预期。项目中但凡存储 LLM 或外部 API 输出的字段，默认用 TEXT。

---

## 问题 8：Redis 缓存 key 不匹配——LLM 参数波动导致 0% 命中率

### 现象

Redis 缓存已启用，工具结果也写入了（可以 `redis-cli keys "taskflow:tool:*"` 看到 4 个 key），但**缓存命中率为 0**：

```
keyspace_hits:0
keyspace_misses:4
```

每次工具调用都真实调了外部 API，缓存形同虚设。

### 原因

缓存 key 用 `SHA-256(工具名 + params.toString())` 计算。LLM 在多次生成同一工具的调用参数时，JSON 格式有微妙差异：

```java
// 第一次: city=太原, params.toString() → "{city=太原}"
// 第二次: city= 太原, params.toString() → "{city= 太原}"  （多了空格！）
// 第三次: City=太原, params.toString() → "{City=太原}"  （大小写不同！）
// 第四次: city=Taiyuan, params.toString() → "{city=Taiyuan}"  （英文！）
```

Map 的 `toString()` 对空格、大小写、key 顺序都敏感，导致同一个逻辑调用产生完全不同的哈希值。**LLM 不保证调用参数的文本一致性**，而缓存依赖精确的字符串匹配。

### 修复

哈希计算前先归一化参数：key 排序（TreeMap）、字符串 value trim、所有 key 统一小写：

```java
private String hashParams(String toolName, Map<String, Object> params) {
    var normalized = new TreeMap<String, Object>();  // 排序 key
    for (var entry : params.entrySet()) {
        Object value = entry.getValue();
        if (value instanceof String s) {
            value = s.trim();  // 去空格
        }
        normalized.put(entry.getKey().trim().toLowerCase(), value);
    }
    String input = toolName + ":" + normalized.toString();
    // SHA-256 hash...
}
```

### 教训

**用 LLM 生成的文本做缓存 key 之前，必须先归一化。** LLM 的输出有天然的随机性——空格、大小写、同义词都可能波动。缓存层必须做好"语义相同 = key 相同"的归一化，否则缓存形同虚设。

这也解释了为什么修复问题 5 的 Prompt + 代码安全阀之后，缓存命中率才能从 0 提升上去——如果 LLM 还在循环调用同一个工具，归一化后的 key 终将匹配。

---

---

## 问题 9：代码安全阀过于粗暴——"同工具 2 次即停"误杀合理多轮搜索

### 现象

问题 5 修复后加入安全阀 `countToolCalls(ctx, toolName) >= 2`（同一工具调用 ≥2 次就强制终止），搜索"JDK 25 新特性"只执行了 1 次 search 就被截断：

```
最终结果: 已完成必要的工具调用
```

用户得到一句废话，没有任何搜索内容。但实际上第一次搜到的是通用摘要，LLM 想再用不同 query 搜一次获取更详细的信息——这是合理的多步搜索。

### 原因

问题 5 的修复从一个极端（不做任何代码限制，LLM 无限循环）走向了另一个极端（按工具名计数，调用 2 次就杀）。一刀切的计数规则无法区分两种场景：

| 场景 | 行为 | 应该 |
|------|------|------|
| weather("太原") × 3 | 完全相同的调用，浪费 | **拦截** |
| search("JDK25 新特性") → search("JDK25 JEP 官方") | 不同 query，合理的深入搜索 | **放行** |

这两种在"同一工具调用次数"上看起来一样，但语义完全不同。

### 修复

改为基于**归一化参数哈希**的精确匹配检测。只有当同一工具 + 同一参数组合再次出现时才拦截：

```java
// 改前：按工具名计数，调用 2 次就杀
if (countToolCalls(ctx, toolName) >= 2) {
    ctx.markCompleted("已完成必要的工具调用");
    break;
}

// 改后：哈希比对工具名+归一化参数，真正相同才拦截
if (isDuplicateCall(ctx, toolName, toolParams)) {
    ctx.markCompleted("已完成必要的工具调用");
    break;
}
```

`isDuplicateCall` 对历史步骤的 `(toolName + normalizedParams)` 做 SHA-256 哈希比较，利用了问题 8 中已有的归一化逻辑。

同时把检查位置从**步骤执行后**提前到**工具调用前**，省掉一次不必要的 API 调用。

### 教训

**安全阀的粒度要和判断依据匹配。** "同一个工具被调了几次"是粗粒度的代理指标，真正应该拦截的是"同一个工具用相同的参数被调了几次"。代理指标在大多数情况下有效，但会误杀边界 case——多轮搜索、分页查询、增量数据拉取等。

用归一化参数哈希做精确匹配后，安全阀的误杀率从"所有同工具多步任务"降到了零。

---

## 问题 10：LLM 输出"该停"但附带空 tool 调用——JSON 模板残留

### 现象

搜索 JDK 25 新特性任务中，LLM 第 3 步的 reasoning 明确说：

> "已经通过两次搜索获取了JDK25新特性的相关结果...这些信息足以满足用户需求，无需再调用其他工具。下一步应结束任务"

但同一段 JSON 里却输出了：

```json
{
  "reasoning": "...无需再调用其他工具...应结束任务",
  "selected_tool": "search",
  "parameters": {"query": null}
}
```

结果：工具执行失败（`query 参数不能为空`），循环没有终止，继续生成更多步骤直到超时。

### 原因

这是 **DeepSeek 小模型在结构化输出上的不稳定**。尽管在 reasoning 中已经做出了正确的语义判断，但在填充 JSON 模板时无法"留空"——模型被训练为始终输出完整的字段结构，即使当前轮次不需要调用任何工具。

本质上是文本 prompt 模式下 function calling 的固有问题：LLM 需要在**同一段文本**中同时完成"判断是否继续"和"如果继续，选什么工具"两个逻辑上互斥的任务。当判断是"不继续"时，后面的字段仍然被模型按惯性填上了默认值。

如果使用 OpenAI 的原生 function calling，工具调用是独立的 `tool_calls` 数组，不选工具时空数组即可，不会出现这种"说不选但又选了"的矛盾。

### 修复

在工具执行前增加**停意检测**：如果 LLM 的 reasoning 包含停止语义关键词，且参数值全为空，则忽略 `selected_tool` 字段，直接按 LLM 的真实意图结束：

```java
// 在选定 tool、构建参数之后、执行工具之前
String reasoning = action.path("reasoning").asText("");
if (isStopIntent(reasoning) && allParamsAreNullOrEmpty(paramsNode)) {
    ctx.markCompleted(reasoning);
    break;
}
```

`isStopIntent` 检测中英文停止关键词：

```java
private boolean isStopIntent(String reasoning) {
    String lower = reasoning.toLowerCase();
    return lower.contains("无需") || lower.contains("结束") || lower.contains("不需要")
            || lower.contains("已经足够") || lower.contains("已经完成")
            || lower.contains("no need") || lower.contains("sufficient");
}
```

`allParamsAreNullOrEmpty` 检查 JSON 参数字段是否全为 null 或空字符串——区分"参数真的为空"和"参数有效但 reasoning 恰好包含停词"。

### 教训

**LLM 的自然语言判断和结构化输出可能互相矛盾。** 当 prompt 要求 LLM 同时输出"意图判断"和"执行指令"时，应该信任意图（reasoning 的自然语言）而非指令（工具名/参数的结构化字段）。因为意图是 LLM 更擅长的，而结构化字段在"不适用"时容易被模型用默认值填充。

更根本的解决方案是**让 LLM 用独立字段表达"该不该继续"**（如 `should_continue: boolean`），已有的 `should_continue` 字段检查应该比工具执行优先。这次漏掉是因为检查顺序问题——先解析了 `selected_tool` 然后就去调工具了，`should_continue: false` 的判断在后面。

---

## 总结：全部 10 个问题

| 编号 | 问题 | 本质 |
|------|------|------|
| 1 | 工具名不匹配 | 过度信任 LLM 输出规范性和精确性 |
| 2 | 失败后放弃 | 让 LLM 参与不该它做的控制流决策 |
| 3 | 反思当结果 | 混淆不同轮次 LLM 对话的职责边界 |
| 4 | 模型名不匹配 | 配置之间缺少一致性校验 |
| 5 | ReAct 循环 + API 重复调用 | LLM 短期失忆 + 缺少代码兜底 |
| 6 | Search API 401 | AI 不查文档，用通用模式替代服务特定协议 |
| 7 | VARCHAR 溢出 | AI 用传统 Web 经验建模 LLM 级数据量 |
| 8 | 缓存 key 0% 命中 | 没有做 LLM 输出的归一化处理 |
| 9 | 安全阀误杀合理多步搜索 | 用粗粒度代理指标替代精确判断 |
| 10 | LLM 口说停手不停 | LLM 的结构化输出和自然语言判断矛盾 |

**10 个问题中有 9 个是过度信任 AI/LLM 的输出。** 唯一例外是问题 4（纯配置）。核心教训始终是一个：**能用代码 guarantee 的事，不要让 AI 或 LLM 自由发挥。**
