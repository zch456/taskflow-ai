package com.taskflow.agent;

import com.taskflow.dto.TaskRequest;

public interface TaskAgent {

    TaskResult execute(String taskDescription);

    TaskResult execute(TaskRequest request);
}
