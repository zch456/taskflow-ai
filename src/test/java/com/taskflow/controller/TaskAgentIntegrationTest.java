package com.taskflow.controller;

import com.taskflow.service.LLMClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TaskAgentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LLMClient llmClient;

    @BeforeEach
    void setUp() {
        when(llmClient.chat(anyString())).thenReturn("""
                {"analysis": "simple task", "steps": [
                  {"step_id": 1, "description": "calculate", "tool": "calculator",
                   "parameters": {"expression": "2+3"}, "expected_output": "5"}
                ]}""");
    }

    @Test
    void shouldExecuteTaskAndReturnResult() throws Exception {
        mockMvc.perform(post("/api/v1/agent/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"task": "计算 2 + 3 的结果", "timeout": 30, "maxSteps": 5}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.executionTimeMs").isNumber());
    }

    @Test
    void shouldCheckTaskStatusAfterExecution() throws Exception {
        // First execute a task
        String responseJson = mockMvc.perform(post("/api/v1/agent/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"task": "计算 1 + 1", "timeout": 30, "maxSteps": 3}"""))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String taskId = extractTaskId(responseJson);

        // Then check its status
        mockMvc.perform(get("/api/v1/agent/status/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId));
    }

    @Test
    void shouldListAvailableTools() throws Exception {
        mockMvc.perform(get("/api/v1/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("calculator"));
    }

    @Test
    void shouldRetrieveTaskDetails() throws Exception {
        String responseJson = mockMvc.perform(post("/api/v1/agent/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"task": "test task details", "timeout": 30, "maxSteps": 3}"""))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String taskId = extractTaskId(responseJson);

        mockMvc.perform(get("/api/v1/agent/" + taskId + "/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId));
    }

    @Test
    void shouldReturnHistory() throws Exception {
        mockMvc.perform(get("/api/v1/agent/history?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void shouldHandleComplexMultiStepTask() throws Exception {
        mockMvc.perform(post("/api/v1/agent/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"task": "perform complex analysis", "timeout": 60, "maxSteps": 10}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").exists());
    }

    private String extractTaskId(String json) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        return mapper.readTree(json).get("taskId").asText();
    }
}
