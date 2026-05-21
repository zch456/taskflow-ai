package com.taskflow.controller;

import com.taskflow.agent.TaskResult;
import com.taskflow.dto.TaskRequest;
import com.taskflow.service.SseService;
import com.taskflow.service.TaskAgentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/agent")
public class TaskAgentController {

    private final TaskAgentService service;
    private final SseService sseService;

    public TaskAgentController(TaskAgentService service, SseService sseService) {
        this.service = service;
        this.sseService = sseService;
    }

    @PostMapping("/execute")
    public ResponseEntity<TaskResult> execute(@Valid @RequestBody TaskRequest request) {
        TaskResult result = service.executeTask(request);
        return ResponseEntity.accepted().body(result);
    }

    @PostMapping("/execute-async")
    public ResponseEntity<TaskResult> executeAsync(@Valid @RequestBody TaskRequest request) {
        TaskResult result = service.executeTaskAsync(request);
        return ResponseEntity.accepted().body(result);
    }

    @GetMapping("/stream/{taskId}")
    public SseEmitter stream(@PathVariable String taskId) {
        return sseService.subscribe(taskId);
    }

    @GetMapping(value = "/stream-sync/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSync(@PathVariable String taskId) {
        var emitter = sseService.subscribe(taskId);
        TaskResult status = service.getTaskStatus(taskId);
        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data(status));
        } catch (Exception ignored) {
        }
        return emitter;
    }

    @GetMapping("/status/{taskId}")
    public ResponseEntity<TaskResult> status(@PathVariable String taskId) {
        TaskResult result = service.getTaskStatus(taskId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/history")
    public ResponseEntity<Page<TaskResult>> history(Pageable pageable) {
        return ResponseEntity.ok(service.getHistory(pageable));
    }

    @GetMapping("/{taskId}/details")
    public ResponseEntity<TaskResult> details(@PathVariable String taskId) {
        return ResponseEntity.ok(service.getTaskDetails(taskId));
    }
}
