package com.taskflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.agent.TaskResult;
import com.taskflow.dto.TaskRequest;
import com.taskflow.entity.ExecutionStatus;
import com.taskflow.service.LLMClient;
import com.taskflow.service.TaskAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TaskAgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskAgentService taskAgentService;

    @MockBean
    private LLMClient llmClient;

    @Test
    void shouldExecuteTask() throws Exception {
        TaskResult result = new TaskResult("task_abc123", ExecutionStatus.COMPLETED,
                "计算结果: 5", Collections.emptyList(), 100L);

        when(taskAgentService.executeTask(any(TaskRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/v1/agent/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"task": "计算 2 + 3", "timeout": 30, "maxSteps": 5}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").value("task_abc123"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result").value("计算结果: 5"));
    }

    @Test
    void shouldRejectEmptyTask() throws Exception {
        mockMvc.perform(post("/api/v1/agent/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"task": "", "timeout": 30}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetTaskStatus() throws Exception {
        TaskResult result = new TaskResult("task_123", ExecutionStatus.RUNNING,
                null, Collections.emptyList(), 500L);

        when(taskAgentService.getTaskStatus("task_123")).thenReturn(result);

        mockMvc.perform(get("/api/v1/agent/status/task_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value("task_123"))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void shouldReturnNotFoundForMissingTask() throws Exception {
        when(taskAgentService.getTaskStatus("nonexistent"))
                .thenThrow(new IllegalArgumentException("Task not found"));

        mockMvc.perform(get("/api/v1/agent/status/nonexistent"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetHistory() throws Exception {
        TaskResult result = new TaskResult("task_1", ExecutionStatus.COMPLETED,
                "done", Collections.emptyList(), 100L);
        when(taskAgentService.getHistory(any()))
                .thenReturn(new PageImpl<>(List.of(result)));

        mockMvc.perform(get("/api/v1/agent/history?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].taskId").value("task_1"))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
    }

    @Test
    void shouldGetTools() throws Exception {
        when(taskAgentService.getAvailableTools()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldGetTaskDetails() throws Exception {
        TaskResult result = new TaskResult("task_456", ExecutionStatus.COMPLETED,
                "result", Collections.emptyList(), 200L);

        when(taskAgentService.getTaskDetails("task_456")).thenReturn(result);

        mockMvc.perform(get("/api/v1/agent/task_456/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value("task_456"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
