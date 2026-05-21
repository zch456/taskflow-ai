package com.taskflow.controller;

import com.taskflow.service.TaskAgentService;
import com.taskflow.tool.Tool;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tools")
public class ToolsController {

    private final TaskAgentService service;

    public ToolsController(TaskAgentService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<Tool>> listTools() {
        return ResponseEntity.ok(service.getAvailableTools());
    }
}
