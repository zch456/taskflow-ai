package com.taskflow.agent;

import com.taskflow.dto.TaskRequest;
import com.taskflow.service.LLMClient;
import com.taskflow.tool.Tool;
import com.taskflow.tool.ToolResult;
import com.taskflow.tool.impl.CalculatorTool;
import com.taskflow.tool.registry.ToolManager;
import com.taskflow.entity.ExecutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskAgentTest {

    private ToolManager toolManager;
    private TaskAgent agent;

    @BeforeEach
    void setUp() {
        toolManager = new ToolManager();
        toolManager.registerTool(new CalculatorTool());

        // Mock LLM client that returns pre-planned responses
        LLMClient mockLlm = new StubLLMClient();
        agent = new TaskAgentImpl(toolManager, mockLlm, 10, 30);
    }

    @Test
    void shouldExecuteSingleStepTask() {
        // Stub LLM plans: just use calculator tool to compute 2+3
        TaskResult result = agent.execute("计算 2 + 3 的结果");

        assertNotNull(result);
        assertNotNull(result.taskId());
        assertEquals(ExecutionStatus.COMPLETED, result.status());
        assertNotNull(result.result());
    }

    @Test
    void shouldExecuteMultiStepTask() {
        TaskResult result = agent.execute("计算 2+3, 然后计算 4*5");

        assertNotNull(result);
        assertEquals(ExecutionStatus.COMPLETED, result.status());
        assertTrue(result.executionSteps().size() >= 1);
    }

    @Test
    void shouldHandleTaskRequest() {
        TaskRequest request = new TaskRequest("计算 1 + 1", 30, 5);
        TaskResult result = agent.execute(request);

        assertNotNull(result);
        assertEquals(ExecutionStatus.COMPLETED, result.status());
    }

    @Test
    void shouldFailGracefullyOnToolError() {
        // This tool always fails
        toolManager.registerTool(new Tool() {
            @Override public String getName() { return "failing_tool"; }
            @Override public String getDescription() { return "Always fails"; }
            @Override public java.util.Map<String, com.taskflow.tool.ParameterSchema> getInputSchema() {
                return java.util.Map.of();
            }
            @Override
            public ToolResult execute(java.util.Map<String, Object> params) {
                return ToolResult.failure("Simulated failure", 0);
            }
        });

        TaskResult result = agent.execute("使用 failing_tool 执行操作");
        assertNotNull(result);
        // Should complete with error recorded, not crash
        assertTrue(result.status() == ExecutionStatus.COMPLETED
                || result.status() == ExecutionStatus.FAILED);
    }

    @Test
    void shouldRespectMaxStepsLimit() {
        TaskRequest request = new TaskRequest("do a very long task", 30, 2);
        TaskResult result = agent.execute(request);

        assertNotNull(result);
        assertTrue(result.executionSteps().size() <= 2);
    }

    @Test
    void shouldReturnResultWithinTimeout() {
        long start = System.currentTimeMillis();
        TaskResult result = agent.execute("简单计算 1+1");
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 5000, "Execution took too long: " + elapsed + "ms");
        assertNotNull(result);
    }

    @Test
    void shouldInvokeProgressCallback() {
        var events = new java.util.ArrayList<com.taskflow.agent.TaskProgressEvent>();
        LLMClient mockLlm = new StubLLMClient();
        TaskAgentImpl agentWithCallback = new TaskAgentImpl(
                toolManager, mockLlm, 10, 30, events::add);

        TaskResult result = agentWithCallback.execute("计算 2 + 3");

        assertNotNull(result);
        assertFalse(events.isEmpty(), "Progress callback should be invoked");
        assertTrue(events.stream().anyMatch(
                e -> e.type() == com.taskflow.agent.TaskProgressEvent.EventType.PLAN_GENERATED));
        assertTrue(events.stream().anyMatch(
                e -> e.type() == com.taskflow.agent.TaskProgressEvent.EventType.TASK_COMPLETED));
    }

    /**
     * Stub LLM client that returns simulated agent reasoning.
     * The stub returns a plan JSON that instructs the agent to use calculator tool.
     */
    private static class StubLLMClient extends LLMClient {

        StubLLMClient() {
            super("http://stub", "no-key", "stub", 0, 100);
        }

        @Override
        public String chat(String prompt) {
            if (prompt.contains("计划") || prompt.contains("规划") || prompt.contains("Plan")) {
                return """
                        {
                          "analysis": "用户需要数学计算",
                          "steps": [
                            {"step_id": 1, "description": "执行计算", "tool": "calculator",
                             "parameters": {"expression": "2+3"}, "expected_output": "计算结果"}
                          ]
                        }""";
            }
            if (prompt.contains("评估") || prompt.contains("是否满足") || prompt.contains("sufficient")) {
                return """
                        {"is_sufficient": true, "reasoning": "计算结果已满足需求"}""";
            }
            return """
                    {"reasoning": "使用计算器", "selected_tool": "calculator",
                     "parameters": {"expression": "2+3"}, "should_continue": false}""";
        }
    }
}
