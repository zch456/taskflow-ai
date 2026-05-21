package com.taskflow.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.taskflow.tool.registry.ToolManager;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolManagerTest {

    private ToolManager toolManager;

    @BeforeEach
    void setUp() {
        toolManager = new ToolManager();
    }

    @Test
    void shouldRegisterAndRetrieveTool() {
        Tool tool = new StubTool("weather", "查询天气");
        toolManager.registerTool(tool);

        Tool retrieved = toolManager.getTool("weather");
        assertNotNull(retrieved);
        assertEquals("weather", retrieved.getName());
    }

    @Test
    void shouldListAllRegisteredTools() {
        toolManager.registerTool(new StubTool("weather", "查询天气"));
        toolManager.registerTool(new StubTool("calculator", "数学计算"));

        List<Tool> tools = toolManager.listAvailableTools();
        assertEquals(2, tools.size());
    }

    @Test
    void shouldReturnToolNamesList() {
        toolManager.registerTool(new StubTool("weather", "查询天气"));
        toolManager.registerTool(new StubTool("search", "网络搜索"));

        List<String> names = toolManager.getToolNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("weather"));
        assertTrue(names.contains("search"));
    }

    @Test
    void shouldThrowExceptionWhenToolNotFound() {
        assertThrows(IllegalArgumentException.class, () ->
                toolManager.getTool("nonexistent"));
    }

    @Test
    void shouldReturnAllToolsAsMap() {
        toolManager.registerTool(new StubTool("weather", "查询天气"));
        Map<String, Tool> tools = toolManager.getAllTools();
        assertEquals(1, tools.size());
        assertTrue(tools.containsKey("weather"));
    }

    private static class StubTool implements Tool {
        private final String name;
        private final String description;

        StubTool(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return description; }

        @Override
        public Map<String, ParameterSchema> getInputSchema() {
            return Map.of();
        }

        @Override
        public ToolResult execute(Map<String, Object> params) {
            return ToolResult.success("ok", 0);
        }
    }
}
