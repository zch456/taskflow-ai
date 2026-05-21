# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
./mvnw spring-boot:run          # Start locally (port 8080, H2 in-memory)
./mvnw test                      # Run all tests (92 tests)
./mvnw test -pl -Dtest=ClassName # Run a single test class
./mvnw clean package -DskipTests # Build JAR
```

## Architecture

TaskFlow AI is a **ReAct-pattern autonomous task execution system**: user submits a natural-language task → LLM plans steps → agent iteratively selects and invokes tools → synthesizes a final answer.

### Core Loop (`TaskAgentImpl.java`)

1. **Plan**: LLM generates a step plan with tool names and parameters (text prompt, not structured function calling)
2. **Act**: Each step, LLM decides the next tool + params based on execution history; `ToolManager.getTool()` resolves the name
3. **Observe**: Tool executes, result goes into `ExecutionContext`
4. **Reflect**: LLM evaluates whether the result satisfies the original task
5. Loop until done, max steps reached, or timeout

### Key Design Decision: Text-Prompt-Based Tool Calling

This project does **not** use native LLM function calling. Tools are described as plain text in prompts, and the LLM returns JSON with a `selected_tool` field. This means the LLM can return tool name variants (e.g., `get_weather` instead of `weather`). `ToolManager.getTool()` includes fuzzy matching to handle this — be careful not to remove it.

### Tool System

- **Interface**: `Tool` — `getName()`, `getDescription()`, `getInputSchema()`, `execute(Map)`
- **Registration**: `ToolRegistration.java` — called `@PostConstruct`, registers tools conditionally based on config (weather/search require `apiBaseUrl != null`)
- **Matching**: `ToolManager.getTool()` — exact match first, then normalized fuzzy match (lowercase + strip underscores)
- **Built-in tools**: `calculator`, `weather`, `search`, `database`, `file_operation`, `json`

### LLM Integration (`LLMClient.java`)

Calls OpenAI-compatible `/v1/chat/completions` endpoint. Base URL and API key come from config. No native function calling — plain `chat(prompt)` returning text, caller parses JSON from the response.

### Configuration Hierarchy

- `application.yml` — local dev (H2, DeepSeek, auth disabled, tool configs with env var placeholders)
- `application-prod.yml` — production (PostgreSQL, Redis, auth enabled, all values via env vars)
- `TaskFlowProperties.java` — `@ConfigurationProperties(prefix = "taskflow")` binds YAML to typed config classes

### API Key Architecture

Three separate API keys, each from different providers:
- `LLM_API_KEY` — AI model (DeepSeek/OpenAI)
- `WEATHER_API_KEY` — weather data (OpenWeatherMap)
- `SEARCH_API_KEY` — web search (Tavily/Bing)

These are NOT interchangeable. The LLM key lets the AI "think"; tool keys let tools fetch real external data.

### Error Code System

`GlobalExceptionHandler` maps exceptions to 12 structured error codes (`ErrorCode` enum). All error responses include `code`, `message`, `traceId`.
