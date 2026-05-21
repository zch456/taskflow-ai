package com.taskflow.service;

import com.taskflow.agent.ExecutionContext;
import com.taskflow.agent.ExecutionStep;
import com.taskflow.agent.TaskAgentImpl;
import com.taskflow.agent.TaskResult;
import com.taskflow.config.TaskFlowProperties;
import com.taskflow.dto.TaskRequest;
import com.taskflow.entity.ExecutionStatus;
import com.taskflow.entity.ExecutionStepEntity;
import com.taskflow.entity.TaskExecutionEntity;
import com.taskflow.exception.ErrorCode;
import com.taskflow.exception.TaskFlowException;
import com.taskflow.repository.TaskExecutionRepository;
import com.taskflow.tool.Tool;
import com.taskflow.tool.registry.ToolManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class TaskAgentService {

    private final TaskAgentImpl agent;
    private final ToolManager toolManager;
    private final TaskExecutionRepository repository;
    private final LLMClient llmClient;
    private final TaskFlowProperties properties;
    private final SseService sseService;
    private final ExecutorService asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public TaskAgentService(ToolManager toolManager, LLMClient llmClient,
                            TaskFlowProperties properties,
                            TaskExecutionRepository repository,
                            SseService sseService) {
        this.toolManager = toolManager;
        this.repository = repository;
        this.llmClient = llmClient;
        this.properties = properties;
        this.sseService = sseService;
        this.agent = new TaskAgentImpl(toolManager, llmClient,
                properties.getAgent().getMaxSteps(),
                properties.getAgent().getTimeoutSeconds());
    }

    @Transactional
    public TaskResult executeTask(TaskRequest request) {
        TaskResult result = agent.execute(request);
        persistResult(result, request);
        return result;
    }

    public TaskResult executeTaskAsync(TaskRequest request) {
        TaskAgentImpl asyncAgent = new TaskAgentImpl(toolManager, llmClient,
                request.maxStepsOrDefault(), request.timeoutOrDefault(),
                sseService::publish);

        CompletableFuture<TaskResult> future = CompletableFuture.supplyAsync(
                () -> {
                    TaskResult result = asyncAgent.execute(request);
                    persistResult(result, request);
                    sseService.complete(result.taskId());
                    return result;
                }, asyncExecutor);

        future.exceptionally(ex -> {
            return null;
        });

        return new TaskResult("pending_" + UUID.randomUUID().toString().substring(0, 8),
                ExecutionStatus.PENDING, "任务已提交，通过 SSE 订阅进度", null, 0);
    }

    public TaskResult getTaskStatus(String taskId) {
        if (taskId.startsWith("pending_")) {
            return new TaskResult(taskId, ExecutionStatus.PENDING,
                    "任务正在排队", null, 0);
        }
        TaskExecutionEntity entity = repository.findByTaskId(taskId)
                .orElseThrow(() -> new TaskFlowException(ErrorCode.TASK_NOT_FOUND,
                        "taskId: " + taskId));
        return toTaskResult(entity);
    }

    @Transactional(readOnly = true)
    public Page<TaskResult> getHistory(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toTaskResult);
    }

    @Transactional(readOnly = true)
    public List<Tool> getAvailableTools() {
        return toolManager.listAvailableTools();
    }

    @Transactional(readOnly = true)
    public TaskResult getTaskDetails(String taskId) {
        TaskExecutionEntity entity = repository.findByTaskId(taskId)
                .orElseThrow(() -> new TaskFlowException(ErrorCode.TASK_NOT_FOUND,
                        "taskId: " + taskId));
        return toTaskResult(entity);
    }

    public ToolManager getToolManager() {
        return toolManager;
    }

    private void persistResult(TaskResult result, TaskRequest request) {
        if (result.taskId().startsWith("pending_")) return;

        TaskExecutionEntity entity = new TaskExecutionEntity(
                result.taskId(),
                "trace_" + UUID.randomUUID().toString().substring(0, 8),
                request.task(),
                result.status(),
                request.maxStepsOrDefault(),
                request.timeoutOrDefault()
        );
        entity.setFinalResult(result.result());
        entity.setExecutionTimeMs(result.executionTimeMs());
        entity.setStartedAt(Instant.now().minusMillis(result.executionTimeMs()));
        entity.setCompletedAt(Instant.now());

        if (result.executionSteps() != null) {
            for (ExecutionStep step : result.executionSteps()) {
                ExecutionStepEntity stepEntity = new ExecutionStepEntity(
                        step.getStepIndex(), step.getAction(),
                        step.getToolName(), step.getInputParams());
                stepEntity.setOutputResult(step.getOutputResult());
                stepEntity.setExecutionTimeMs(step.getExecutionTimeMs());
                stepEntity.setSuccess(step.isSuccess());
                stepEntity.setErrorMessage(step.getErrorMessage());
                entity.addStep(stepEntity);
            }
        }

        repository.save(entity);
    }

    private TaskResult toTaskResult(TaskExecutionEntity entity) {
        List<ExecutionStep> steps = entity.getSteps().stream()
                .map(this::toExecutionStep)
                .toList();
        return new TaskResult(entity.getTaskId(), entity.getStatus(),
                entity.getFinalResult(), steps, entity.getExecutionTimeMs());
    }

    private ExecutionStep toExecutionStep(ExecutionStepEntity entity) {
        ExecutionStep step = new ExecutionStep(
                entity.getStepIndex(), entity.getAction(),
                entity.getToolName(), entity.getInputParams());
        step.setOutputResult(entity.getOutputResult());
        step.setExecutionTimeMs(entity.getExecutionTimeMs());
        step.setSuccess(entity.isSuccess());
        step.setErrorMessage(entity.getErrorMessage());
        return step;
    }
}
