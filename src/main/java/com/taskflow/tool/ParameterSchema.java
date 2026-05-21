package com.taskflow.tool;

public record ParameterSchema(
        String type,
        String description,
        boolean required
) {}
