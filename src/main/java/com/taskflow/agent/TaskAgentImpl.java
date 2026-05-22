package com.taskflow.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.dto.TaskRequest;
import com.taskflow.entity.ExecutionStatus;
import com.taskflow.service.LLMClient;
import com.taskflow.service.RedisCacheService;
import com.taskflow.tool.Tool;
import com.taskflow.tool.ToolResult;
import com.taskflow.tool.registry.ToolManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Consumer;

public class TaskAgentImpl implements TaskAgent {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final ToolManager toolManager;
    private final LLMClient llmClient;
    private final int maxSteps;
    private final int timeoutSeconds;
    private final Consumer<TaskProgressEvent> progressCallback;
    private final RedisCacheService cacheService;

    public TaskAgentImpl(ToolManager toolManager, LLMClient llmClient,
                         int maxSteps, int timeoutSeconds) {
        this(toolManager, llmClient, maxSteps, timeoutSeconds, null, null);
    }

    public TaskAgentImpl(ToolManager toolManager, LLMClient llmClient,
                         int maxSteps, int timeoutSeconds,
                         Consumer<TaskProgressEvent> progressCallback) {
        this(toolManager, llmClient, maxSteps, timeoutSeconds, progressCallback, null);
    }

    public TaskAgentImpl(ToolManager toolManager, LLMClient llmClient,
                         int maxSteps, int timeoutSeconds,
                         Consumer<TaskProgressEvent> progressCallback,
                         RedisCacheService cacheService) {
        this.toolManager = toolManager;
        this.llmClient = llmClient;
        this.maxSteps = maxSteps;
        this.timeoutSeconds = timeoutSeconds;
        this.progressCallback = progressCallback;
        this.cacheService = cacheService;
    }

    @Override
    public TaskResult execute(String taskDescription) {
        return execute(new TaskRequest(taskDescription, timeoutSeconds, maxSteps));
    }

    @Override
    public TaskResult execute(TaskRequest request) {
        ExecutionContext ctx = new ExecutionContext(
                request.task(), request.maxStepsOrDefault(), request.timeoutOrDefault());

        long startTime = System.currentTimeMillis();

        try {
            ctx.markRunning();

            // Step 1: Generate plan
            String planPrompt = buildPlanPrompt(ctx.getOriginalTask());
            String planResponse = llmClient.chat(planPrompt);

            Plan plan = parsePlan(planResponse);
            emitProgress(TaskProgressEvent.planGenerated(ctx.getTaskId(), plan.analysis()));

            // Step 2: Iterative execution loop
            while (!ctx.hasReachedMaxSteps() && !ctx.isExpired()) {
                if (ctx.getStatus() == ExecutionStatus.COMPLETED) {
                    break;
                }

                // Step 3.2: Decide next action with LLM
                String actionPrompt = buildActionPrompt(ctx, plan);
                String actionResponse = llmClient.chat(actionPrompt);
                JsonNode action = mapper.readTree(actionResponse);

                // Step 3.3: Select tool
                String toolName = action.path("selected_tool").asText();
                JsonNode paramsNode = action.path("parameters");

                if (toolName == null || toolName.isEmpty()) {
                    toolName = getNextPlannedTool(plan, ctx.getStepCount());
                }

                if (toolName == null || toolName.isEmpty()) {
                    ctx.markCompleted("LLM判断任务无需继续执行工具");
                    break;
                }

                // Detect LLM stop intent: reasoning says done but extraneous tool was output
                String reasoning = action.path("reasoning").asText("");
                if (isStopIntent(reasoning) && allParamsAreNullOrEmpty(paramsNode)) {
                    ctx.markCompleted(reasoning);
                    break;
                }

                // Step 3.4: Execute tool
                int stepIndex = ctx.getStepCount() + 1;
                ExecutionStep step = new ExecutionStep(
                        stepIndex,
                        action.path("reasoning").asText("执行工具: " + toolName),
                        toolName,
                        paramsNode.toString()
                );

                emitProgress(TaskProgressEvent.stepStarted(ctx.getTaskId(), stepIndex,
                        toolName, paramsNode));

                @SuppressWarnings("unchecked")
                Map<String, Object> toolParams = mapper.convertValue(paramsNode, Map.class);

                // Safety valve: if same tool+params already called, skip and exit loop
                if (isDuplicateCall(ctx, toolName, toolParams)) {
                    ctx.markCompleted("已完成必要的工具调用");
                    break;
                }

                try {
                    Tool tool = toolManager.getTool(toolName);
                    ToolResult toolResult = executeWithRetry(tool, toolParams);

                    step.complete(toolResult);
                    ctx.addStep(step);
                    ctx.getVariables().put(toolName + "_result", toolResult.data());

                    if (toolResult.success()) {
                        emitProgress(TaskProgressEvent.stepCompleted(
                                ctx.getTaskId(), stepIndex, toolResult));
                    } else {
                        emitProgress(TaskProgressEvent.stepFailed(
                                ctx.getTaskId(), stepIndex, toolResult.errorMessage()));
                    }

                } catch (IllegalArgumentException e) {
                    step.complete(ToolResult.failure("工具不存在: " + toolName, 0));
                    ctx.addStep(step);
                    emitProgress(TaskProgressEvent.stepFailed(
                            ctx.getTaskId(), stepIndex, "工具不存在: " + toolName));
                }

                // Step 3.5: Reflection (skip if step failed — let LLM retry with a different tool)
                if (step.isSuccess()) {
                    String reflectPrompt = buildReflectPrompt(ctx);
                    String reflectResponse = llmClient.chat(reflectPrompt);
                    JsonNode reflection = mapper.readTree(reflectResponse);

                    if (reflection.path("is_sufficient").asBoolean(false)) {
                        ctx.markCompleted();
                        break;
                    }

                    boolean shouldContinue = action.path("should_continue").asBoolean(true);
                    if (!shouldContinue) {
                        ctx.markCompleted(ctx.getFinalResult() != null
                                ? ctx.getFinalResult() : "任务执行完成");
                        break;
                    }
                }

            }

            // Handle timeout / max steps reached
            if (ctx.isExpired() && ctx.getStatus() == ExecutionStatus.RUNNING) {
                ctx.markTimeout();
                emitProgress(TaskProgressEvent.taskTimeout(ctx.getTaskId()));
            } else if (ctx.hasReachedMaxSteps() && ctx.getStatus() == ExecutionStatus.RUNNING) {
                ctx.markCompleted("已达到最大步数限制，任务终止");
            } else if (ctx.getStatus() == ExecutionStatus.RUNNING) {
                ctx.markCompleted("任务执行完成");
            }

        } catch (Exception e) {
            ctx.markFailed("执行异常: " + e.getMessage());
            emitProgress(TaskProgressEvent.taskFailed(ctx.getTaskId(), e.getMessage()));
        }

        // Step 4: Synthesize final result
        String finalResult = ctx.getFinalResult();
        if (finalResult == null) {
            String synthesisPrompt = buildSynthesisPrompt(ctx);
            finalResult = llmClient.chat(synthesisPrompt);
        }

        long totalTime = System.currentTimeMillis() - startTime;

        if (ctx.getStatus() == ExecutionStatus.COMPLETED) {
            emitProgress(TaskProgressEvent.taskCompleted(ctx.getTaskId(), finalResult));
        }

        return new TaskResult(ctx.getTaskId(), ctx.getStatus(), finalResult,
                ctx.getExecutionHistory(), totalTime);
    }

    private void emitProgress(TaskProgressEvent event) {
        if (progressCallback != null) {
            progressCallback.accept(event);
        }
    }

    private ToolResult executeWithRetry(Tool tool, Map<String, Object> params) {
        // Check cache for identical tool call
        if (cacheService != null) {
            String paramsHash = hashParams(tool.getName(), params);
            var cached = cacheService.getCachedToolResult(tool.getName(), paramsHash);
            if (cached.isPresent()) {
                return (ToolResult) cached.get();
            }
        }

        ToolResult result = tool.execute(params);
        for (int i = 0; i < tool.getMaxRetries() && !result.success(); i++) {
            result = tool.execute(params);
        }

        // Cache successful results
        if (cacheService != null && result.success()) {
            String paramsHash = hashParams(tool.getName(), params);
            cacheService.cacheToolResult(tool.getName(), paramsHash,
                    result, Duration.ofMinutes(10));
        }

        return result;
    }

    private String hashParams(String toolName, Map<String, Object> params) {
        try {
            // Normalize to a deterministic string: sorted keys, trimmed values
            var normalized = new java.util.TreeMap<String, Object>();
            for (var entry : params.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String s) {
                    value = s.trim();
                }
                normalized.put(entry.getKey().trim(), value);
            }
            String input = toolName + ":" + normalized.toString();

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString((toolName + params).hashCode());
        }
    }

    private String buildPlanPrompt(String task) {
        StringBuilder toolsDesc = new StringBuilder();
        for (Tool t : toolManager.listAvailableTools()) {
            toolsDesc.append("- ").append(t.getName())
                    .append(": ").append(t.getDescription()).append("\n");
        }

        return """
               用户任务: %s

               可用工具:
               %s

               请分析这个任务，规划完成它需要的步骤，以JSON格式返回:
               {
                 "analysis": "任务分析",
                 "steps": [{"step_id": 1, "description": "步骤描述", "tool": "工具名",
                            "parameters": {"param1": "value"}, "expected_output": "预期输出"}]
               }
               """.formatted(task, toolsDesc.toString());
    }

    private String buildActionPrompt(ExecutionContext ctx, Plan plan) {
        StringBuilder toolsDesc = new StringBuilder();
        for (Tool t : toolManager.listAvailableTools()) {
            toolsDesc.append("- ").append(t.getName()).append("\n");
        }

        return """
               可用工具(必须使用以下工具名):
               %s
               当前目标: %s
               已执行步骤: %d

               已执行步骤的详细结果:
               %s

               根据当前情况，从可用工具中选择下一步应使用的工具。注意：如果之前已调用过某个工具并获得有效结果，不要重复调用。如果任务目标已达成，设置 should_continue=false 结束任务。返回JSON:
               {
                 "reasoning": "选择原因",
                 "selected_tool": "工具名",
                 "parameters": {"param1": "value"},
                 "should_continue": true/false
               }
               """.formatted(toolsDesc.toString(), ctx.getOriginalTask(), ctx.getStepCount(),
                buildFullHistory(ctx));
    }

    private String buildReflectPrompt(ExecutionContext ctx) {
        return """
               原始任务: %s
               已执行步骤: %d

               已执行步骤的详细结果:
               %s

               评估上述所有步骤的结果是否已经充分回答了原始任务。如果信息已足够回答用户问题，返回 is_sufficient=true，不要继续索取更多信息。返回JSON:
               {"is_sufficient": true/false, "reasoning": "理由"}
               """.formatted(ctx.getOriginalTask(), ctx.getStepCount(),
                buildFullHistory(ctx));
    }

    private String buildSynthesisPrompt(ExecutionContext ctx) {
        StringBuilder history = new StringBuilder();
        for (ExecutionStep step : ctx.getExecutionHistory()) {
            history.append("步骤").append(step.getStepIndex())
                    .append(" (").append(step.getToolName()).append("): ")
                    .append(step.getOutputResult()).append("\n");
        }

        return """
               请根据以下工具执行结果，生成用户友好的最终答案。
               原始任务: %s

               工具执行结果:
               %s

               要求: 将原始数据整理成自然语言回答，清晰呈现关键信息（如温度、天气状况等），
               让用户一目了然。直接返回答案，不要用JSON格式，不要描述执行过程。
               """.formatted(ctx.getOriginalTask(), history.toString());
    }

    private String buildFullHistory(ExecutionContext ctx) {
        if (ctx.getExecutionHistory().isEmpty()) return "无";
        var sb = new StringBuilder();
        for (ExecutionStep step : ctx.getExecutionHistory()) {
            sb.append("步骤").append(step.getStepIndex())
                    .append(" [").append(step.getToolName()).append("]: ");
            if (step.getOutputResult() != null) {
                String output = step.getOutputResult();
                // Truncate very long outputs to prevent prompt overflow
                if (output.length() > 800) {
                    output = output.substring(0, 800) + "...(截断)";
                }
                sb.append(output);
            } else {
                sb.append("执行失败: ").append(step.getErrorMessage());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private boolean isStopIntent(String reasoning) {
        if (reasoning == null || reasoning.isBlank()) return false;
        String lower = reasoning.toLowerCase();
        return lower.contains("无需") || lower.contains("结束") || lower.contains("不需要")
                || lower.contains("已经足够") || lower.contains("已经完成")
                || lower.contains("no need") || lower.contains("sufficient");
    }

    private boolean allParamsAreNullOrEmpty(JsonNode paramsNode) {
        if (paramsNode.isEmpty()) return true;
        var fields = paramsNode.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            JsonNode value = entry.getValue();
            if (!value.isNull() && !value.asText("").isBlank()) {
                return false;
            }
        }
        return true;
    }

    private boolean isDuplicateCall(ExecutionContext ctx, String toolName,
                                      Map<String, Object> params) {
        String currentHash = hashParams(toolName, params);
        return ctx.getExecutionHistory().stream()
                .filter(s -> toolName.equals(s.getToolName()))
                .anyMatch(s -> currentHash.equals(hashParams(toolName,
                        parseParams(s.getInputParams()))));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParams(String inputParams) {
        try {
            return mapper.readValue(inputParams, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String getNextPlannedTool(Plan plan, int currentStep) {
        if (plan == null || plan.steps() == null || plan.steps().isEmpty()) return null;
        int idx = Math.min(currentStep, plan.steps().size() - 1);
        return plan.steps().get(idx).tool();
    }

    private Plan parsePlan(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            return new Plan(
                    root.path("analysis").asText(""),
                    mapper.convertValue(root.path("steps"), mapper.getTypeFactory()
                            .constructCollectionType(java.util.List.class, Plan.PlannedStep.class))
            );
        } catch (JsonProcessingException e) {
            return new Plan("无法解析计划", java.util.List.of());
        }
    }
}
