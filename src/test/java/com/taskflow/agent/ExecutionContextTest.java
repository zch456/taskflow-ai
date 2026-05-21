package com.taskflow.agent;

import com.taskflow.entity.ExecutionStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionContextTest {

    @Test
    void shouldInitializeWithPendingStatus() {
        ExecutionContext ctx = new ExecutionContext("test task", 10, 30);

        assertEquals(ExecutionStatus.PENDING, ctx.getStatus());
        assertEquals("test task", ctx.getOriginalTask());
        assertNotNull(ctx.getTaskId());
        assertNotNull(ctx.getTraceId());
        assertEquals(0, ctx.getStepCount());
    }

    @Test
    void shouldTransitionThroughStatuses() {
        ExecutionContext ctx = new ExecutionContext("test", 10, 30);

        ctx.markRunning();
        assertEquals(ExecutionStatus.RUNNING, ctx.getStatus());

        ctx.markCompleted("result");
        assertEquals(ExecutionStatus.COMPLETED, ctx.getStatus());
        assertEquals("result", ctx.getFinalResult());
    }

    @Test
    void shouldMarkFailed() {
        ExecutionContext ctx = new ExecutionContext("test", 10, 30);
        ctx.markFailed("error occurred");
        assertEquals(ExecutionStatus.FAILED, ctx.getStatus());
        assertEquals("error occurred", ctx.getFinalResult());
    }

    @Test
    void shouldMarkTimeout() {
        ExecutionContext ctx = new ExecutionContext("test", 10, 30);
        ctx.markTimeout();
        assertEquals(ExecutionStatus.TIMEOUT, ctx.getStatus());
    }

    @Test
    void shouldDetectMaxStepsReached() {
        ExecutionContext ctx = new ExecutionContext("test", 2, 30);

        assertFalse(ctx.hasReachedMaxSteps());

        ctx.addStep(new ExecutionStep(1, "action1", "tool1", "{}"));
        ctx.addStep(new ExecutionStep(2, "action2", "tool2", "{}"));

        assertTrue(ctx.hasReachedMaxSteps());
    }

    @Test
    void shouldTrackExecutionHistory() {
        ExecutionContext ctx = new ExecutionContext("test", 10, 30);

        ExecutionStep step1 = new ExecutionStep(1, "query weather", "weather", "{\"city\":\"Beijing\"}");
        ExecutionStep step2 = new ExecutionStep(2, "analyze", "reasoning", "{\"data\":\"weather result\"}");

        ctx.addStep(step1);
        ctx.addStep(step2);

        assertEquals(2, ctx.getStepCount());
        assertEquals(2, ctx.getExecutionHistory().size());
        assertEquals("weather", ctx.getExecutionHistory().get(0).getToolName());
    }

    @Test
    void shouldIncrementRetryCount() {
        ExecutionContext ctx = new ExecutionContext("test", 10, 30);
        assertEquals(0, ctx.getRetryCount());
        ctx.incrementRetry();
        assertEquals(1, ctx.getRetryCount());
        ctx.incrementRetry();
        assertEquals(2, ctx.getRetryCount());
    }

    @Test
    void shouldHaveDefaultTimeoutNotExpired() {
        ExecutionContext ctx = new ExecutionContext("test", 10, 30);
        assertFalse(ctx.isExpired());
    }

    @Test
    void shouldStoreAndRetrieveVariables() {
        ExecutionContext ctx = new ExecutionContext("test", 10, 30);

        ctx.getVariables().put("weather_data", "sunny");
        assertEquals("sunny", ctx.getVariables().get("weather_data"));
    }

    @Test
    void shouldTrackElapsedTime() {
        ExecutionContext ctx = new ExecutionContext("test", 10, 30);
        long elapsed = ctx.getElapsedMs();
        assertTrue(elapsed >= 0);
    }
}
