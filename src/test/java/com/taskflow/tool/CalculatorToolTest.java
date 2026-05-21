package com.taskflow.tool;

import com.taskflow.tool.impl.CalculatorTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CalculatorToolTest {

    private CalculatorTool calculator;

    @BeforeEach
    void setUp() {
        calculator = new CalculatorTool();
    }

    @Test
    void shouldReturnCorrectName() {
        assertEquals("calculator", calculator.getName());
    }

    @Test
    void shouldHaveDescription() {
        assertNotNull(calculator.getDescription());
        assertFalse(calculator.getDescription().isEmpty());
    }

    @Test
    void shouldHaveInputSchema() {
        Map<String, ParameterSchema> schema = calculator.getInputSchema();
        assertTrue(schema.containsKey("expression"));
        assertTrue(schema.get("expression").required());
    }

    @Test
    void shouldEvaluateSimpleAddition() {
        ToolResult result = calculator.execute(Map.of("expression", "2 + 3"));
        assertTrue(result.success());
        assertEquals("5.0", result.data().toString());
    }

    @Test
    void shouldEvaluateMultiplication() {
        ToolResult result = calculator.execute(Map.of("expression", "4 * 5"));
        assertTrue(result.success());
        assertEquals("20.0", result.data().toString());
    }

    @Test
    void shouldEvaluateComplexExpression() {
        ToolResult result = calculator.execute(Map.of("expression", "2 + 3 * 4"));
        assertTrue(result.success());
        assertEquals("14.0", result.data().toString());
    }

    @Test
    void shouldEvaluateDivision() {
        ToolResult result = calculator.execute(Map.of("expression", "10 / 3"));
        assertTrue(result.success());
        assertNotNull(result.data());
    }

    @Test
    void shouldHandleInvalidExpression() {
        ToolResult result = calculator.execute(Map.of("expression", "2 + abc"));
        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    void shouldReturnExecutionTime() {
        ToolResult result = calculator.execute(Map.of("expression", "1 + 1"));
        assertTrue(result.executionTimeMs() >= 0);
    }

    @Test
    void shouldNotBeRetryableByDefault() {
        assertEquals(0, calculator.getMaxRetries());
    }
}
