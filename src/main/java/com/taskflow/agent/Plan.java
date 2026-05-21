package com.taskflow.agent;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record Plan(
        String analysis,
        List<PlannedStep> steps
) {
    public record PlannedStep(
            @JsonProperty("step_id") int stepId,
            String description,
            String tool,
            java.util.Map<String, Object> parameters,
            @JsonProperty("expected_output") String expectedOutput
    ) {}
}
