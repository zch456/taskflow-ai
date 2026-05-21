package com.taskflow.tool;

import com.taskflow.tool.impl.JsonTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonToolTest {

    private JsonTool jsonTool;

    @BeforeEach
    void setUp() {
        jsonTool = new JsonTool();
    }

    @Test
    void shouldReturnCorrectName() {
        assertEquals("json", jsonTool.getName());
    }

    @Test
    void shouldHaveDescription() {
        assertNotNull(jsonTool.getDescription());
        assertFalse(jsonTool.getDescription().isEmpty());
    }

    @Test
    void shouldHaveInputSchema() {
        Map<String, ParameterSchema> schema = jsonTool.getInputSchema();
        assertTrue(schema.containsKey("operation"));
        assertTrue(schema.get("operation").required());
        assertTrue(schema.containsKey("json"));
        assertTrue(schema.get("json").required());
    }

    @Test
    void shouldParseValidJsonObject() {
        String json = "{\"name\":\"Alice\",\"age\":30}";
        ToolResult result = jsonTool.execute(
                Map.of("operation", "parse", "json", json));
        assertTrue(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();
        assertEquals(true, data.get("valid"));
        assertEquals("OBJECT", data.get("type"));
        assertEquals(2, data.get("fieldCount"));
    }

    @Test
    void shouldParseValidJsonArray() {
        String json = "[1, 2, 3, 4, 5]";
        ToolResult result = jsonTool.execute(
                Map.of("operation", "parse", "json", json));
        assertTrue(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();
        assertEquals("ARRAY", data.get("type"));
        assertEquals(5, data.get("elementCount"));
    }

    @Test
    void shouldRejectInvalidJson() {
        String json = "{name: invalid}";
        ToolResult result = jsonTool.execute(
                Map.of("operation", "parse", "json", json));
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("JSON"));
    }

    @Test
    void shouldQueryNestedProperty() {
        String json = "{\"user\":{\"profile\":{\"name\":\"Alice\",\"city\":\"NYC\"}}}";
        ToolResult result = jsonTool.execute(
                Map.of("operation", "query", "json", json, "expression", "user.profile.name"));
        assertTrue(result.success());
        assertEquals("Alice", result.data());
    }

    @Test
    void shouldQueryPropertyFromArray() {
        String json = "[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}]";
        ToolResult result = jsonTool.execute(
                Map.of("operation", "query", "json", json, "expression", "name"));
        assertTrue(result.success());
        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) result.data();
        assertEquals(2, names.size());
        assertEquals("Alice", names.get(0));
        assertEquals("Bob", names.get(1));
    }

    @Test
    void shouldQueryArrayByIndex() {
        String json = "[{\"name\":\"Alice\"},{\"name\":\"Bob\"}]";
        ToolResult result = jsonTool.execute(
                Map.of("operation", "query", "json", json, "expression", "1.name"));
        assertTrue(result.success());
        assertEquals("Bob", result.data());
    }

    @Test
    void shouldReturnFailureForNonexistentPath() {
        String json = "{\"name\":\"Alice\"}";
        ToolResult result = jsonTool.execute(
                Map.of("operation", "query", "json", json, "expression", "address.city"));
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("路径未找到"));
    }

    @Test
    void shouldTransformRenameFields() {
        String json = "{\"first_name\":\"Alice\",\"last_name\":\"Smith\",\"age\":30}";
        ToolResult result = jsonTool.execute(
                Map.of("operation", "transform", "json", json,
                        "expression", "first_name:firstName,last_name:lastName"));
        assertTrue(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();
        assertEquals("Alice", data.get("firstName"));
        assertEquals("Smith", data.get("lastName"));
        assertEquals(30, data.get("age"));
    }

    @Test
    void shouldRejectEmptyOperation() {
        ToolResult result = jsonTool.execute(
                Map.of("operation", "", "json", "{}"));
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("不能为空"));
    }
}
