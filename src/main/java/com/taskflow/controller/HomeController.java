package com.taskflow.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class HomeController {

    @GetMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object home() {
        return java.util.Map.of(
                "service", "TaskFlow AI",
                "version", "1.0.0",
                "status", "running",
                "docs", "/api/v1/agent",
                "endpoints", java.util.List.of(
                        "POST /api/v1/agent/execute",
                        "GET  /api/v1/agent/status/{taskId}",
                        "GET  /api/v1/agent/history",
                        "GET  /api/v1/agent/{taskId}/details",
                        "GET  /api/v1/tools",
                        "GET  /actuator/health"
                ),
                "timestamp", Instant.now().toString()
        );
    }
}
