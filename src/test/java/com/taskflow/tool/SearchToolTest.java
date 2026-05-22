package com.taskflow.tool;

import com.taskflow.tool.impl.SearchTool;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SearchToolTest {

    private MockWebServer mockServer;
    private SearchTool searchTool;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        searchTool = new SearchTool(mockServer.url("/").toString(), "test-key");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Test
    void shouldReturnCorrectName() {
        assertEquals("search", searchTool.getName());
    }

    @Test
    void shouldRequireQueryParameter() {
        Map<String, ParameterSchema> schema = searchTool.getInputSchema();
        assertTrue(schema.containsKey("query"));
        assertTrue(schema.get("query").required());
    }

    @Test
    void shouldReturnSearchResults() {
        mockServer.enqueue(new MockResponse()
                .setBody("""
                        {
                          "results": [
                            {"title": "Result 1", "url": "https://example.com", "snippet": "Snippet 1"},
                            {"title": "Result 2", "url": "https://example.org", "snippet": "Snippet 2"}
                          ]
                        }""")
                .addHeader("Content-Type", "application/json"));

        ToolResult result = searchTool.execute(Map.of("query", "Java tutorial"));
        assertTrue(result.success());
        assertNotNull(result.data());
    }

    @Test
    void shouldHandleMissingQuery() {
        ToolResult result = searchTool.execute(Map.of());
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("query"));
    }

    @Test
    void shouldHandleApiErrorResponse() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("Unauthorized"));

        ToolResult result = searchTool.execute(Map.of("query", "test"));
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("HTTP 401"));
    }
}
