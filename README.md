# TaskFlow AI

基于 ReAct 模式的自主任务执行系统。用户提交自然语言任务 → LLM 规划步骤 → Agent 迭代选择并调用工具 → 合成最终结果。

**技术栈**: Java 21, Spring Boot 3.2, PostgreSQL, Redis, DeepSeek V4, OkHttp, JUnit 5

## 架构

```
用户任务 (自然语言)
    │
    ▼
┌──────────────┐     ┌──────────┐     ┌──────────────┐
│  TaskAgent   │────▶│ LLMClient│────▶│  DeepSeek    │
│  (ReAct 循环) │     │          │     │  (V4 Flash)  │
└──────┬───────┘     └──────────┘     └──────────────┘
       │
       ▼
┌──────────────┐     ┌──────────┐     ┌──────────────┐
│  ToolManager │────▶│  Tools   │────▶│ 外部 API     │
│  (模糊匹配)   │     │          │     │ Weather/Search│
└──────────────┘     └──────────┘     └──────────────┘
       │
       ▼
┌──────────────┐     ┌──────────┐     ┌──────────────┐
│ PostgreSQL   │     │  Redis   │     │  前端 SPA    │
│ (持久存储)   │     │(缓存+幂等)│     │  (管理界面)  │
└──────────────┘     └──────────┘     └──────────────┘
```

### ReAct 循环流程

1. **Plan** — LLM 分析任务，生成步骤计划
2. **Act** — 每步由 LLM 选择工具 + 参数，ToolManager 解析并执行
3. **Observe** — 工具结果写入 ExecutionContext，写入全量历史到下一轮 Prompt
4. **Reflect** — LLM 评估结果是否满足任务，决定继续或结束
5. 循环直到完成 / 超时 / 安全阀触发

### 关键设计决策

- **文本 Prompt 模式**（非原生 function calling）：工具列表以纯文本嵌入 Prompt，LLM 返回 JSON 格式的 `selected_tool`。ToolManager 包含模糊匹配（去下划线、小写化）处理 LLM 返回的工具名变体。
- **三层循环终止机制**：LLM 决策（action + reflection）、代码安全阀（同工具+同参数重复检测）、超时/最大步数限制。

## 快速启动

### 前置条件

- Java 21+
- PostgreSQL（默认 localhost:5432/taskflow）
- Redis（默认 localhost:6379）
- DeepSeek API Key（或兼容 OpenAI 接口的 LLM）
- WeatherAPI Key（可选）
- Tavily Search API Key（可选）

### 1. 创建数据库

```bash
psql -U postgres -c "CREATE USER taskflow WITH PASSWORD 'taskflow';"
psql -U postgres -c "CREATE DATABASE taskflow OWNER taskflow;"
psql -U postgres -d taskflow -c "GRANT ALL ON SCHEMA public TO taskflow;"
```

### 2. 配置环境变量

在 IDEA Run Configuration 中设置：

| 变量 | 说明 | 必填 |
|------|------|------|
| `LLM_API_KEY` | DeepSeek API Key | 是 |
| `WEATHER_API_KEY` | WeatherAPI Key | 否 |
| `SEARCH_API_KEY` | Tavily Search API Key | 否 |
| `SEARCH_API_BASE_URL` | 搜索 API 地址 | 否（默认空，不注册 search 工具） |
| `WEATHER_API_BASE_URL` | 天气 API 地址 | 否（默认 WeatherAPI） |
| `DB_HOST/PORT/NAME/USER/PASSWORD` | PostgreSQL 连接信息 | 否（默认 localhost:5432/taskflow） |
| `REDIS_HOST/PORT` | Redis 连接信息 | 否（默认 localhost:6379） |

### 3. 启动

```bash
./mvnw spring-boot:run
```

启动后访问：
- 管理界面：`http://localhost:8080/`
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- 健康检查：`http://localhost:8080/actuator/health`

## API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api` | API 导航 |
| `GET` | `/api/v1/tools` | 列出可用工具 |
| `POST` | `/api/v1/agent/execute` | 提交任务（同步） |
| `POST` | `/api/v1/agent/execute-async` | 提交任务（异步，SSE 推送） |
| `GET` | `/api/v1/agent/status/{taskId}` | 查询任务状态 |
| `GET` | `/api/v1/agent/{taskId}/details` | 查询任务详情（含步骤） |
| `GET` | `/api/v1/agent/history` | 查询执行历史（分页） |
| `GET` | `/api/v1/agent/stream/{taskId}` | SSE 订阅任务进度 |

```bash
# 提交任务示例
curl -X POST http://localhost:8080/api/v1/agent/execute \
  -H "Content-Type: application/json" \
  -d '{"task": "查询北京天气并推荐户外活动", "timeout": 60, "maxSteps": 10}'

# 幂等提交（防止重复执行）
curl -X POST http://localhost:8080/api/v1/agent/execute \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: my-unique-key-001" \
  -d '{"task": "计算 100 * 25 + 300", "timeout": 30, "maxSteps": 5}'
```

## 内置工具

| 工具 | 名称 | 说明 |
|------|------|------|
| Calculator | `calculator` | 数学表达式计算 |
| Weather | `weather` | 天气查询（WeatherAPI） |
| Search | `search` | 网络搜索（Tavily） |
| Database | `database` | H2/PostgreSQL SELECT 查询 |
| File Operation | `file_operation` | 文件读写/列表/删除 |
| JSON | `json` | JSON 解析/查询/转换 |

工具通过 `ToolRegistration` 按条件注册——weather 和 search 需要配置 `apiBaseUrl` 才会注册。

## 配置说明

核心配置在 `application.yml`：

```yaml
taskflow:
  agent:
    max-steps: 10          # 最大执行步数
    timeout-seconds: 60    # 超时时间
    cache-enabled: true    # Redis 工具结果缓存
    idempotency-enabled: true  # 幂等性（防重复提交）
  llm:
    provider: deepseek
    model: deepseek-v4-flash
    temperature: 0.7
    max-tokens: 2000
```

所有数据库和 Redis 连接信息均通过环境变量配置，详见 `application.yml`。

## 运行测试

```bash
./mvnw test                          # 全部 93 个测试
./mvnw test -Dtest=SearchToolTest    # 单个测试类
```

测试使用 MockWebServer 模拟外部 API，StubLLMClient 替代真实 LLM，不花钱不联网。详见 `docs/测试用例.md`。

## 踩坑记录

本项目在开发过程中遇到并解决了 10 个与 AI 代码生成和 LLM 行为相关的实际问题，包括：

- LLM 不遵守工具名、失败后放弃、反思文本泄漏到用户输出
- ReAct 循环无限交替导致 API 重复调用超时
- 搜索 API 认证方式不匹配、数据库 VARCHAR 溢出
- Redis 缓存 key 因 LLM 参数波动导致 0% 命中率

详见 **[docs/vibe-coding-issues.md](docs/vibe-coding-issues.md)**。
