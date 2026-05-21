package com.taskflow.tool;

import com.taskflow.tool.impl.WeatherTool;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WeatherToolTest {

    private MockWebServer mockServer;
    private WeatherTool weatherTool;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        weatherTool = new WeatherTool(mockServer.url("/").toString(), "test-key");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Test
    void shouldReturnCorrectName() {
        assertEquals("weather", weatherTool.getName());
    }

    @Test
    void shouldHaveDescription() {
        assertNotNull(weatherTool.getDescription());
        assertFalse(weatherTool.getDescription().isEmpty());
    }

    @Test
    void shouldRequireCityParameter() {
        Map<String, ParameterSchema> schema = weatherTool.getInputSchema();
        assertTrue(schema.containsKey("city"));
        assertTrue(schema.get("city").required());
    }

    @Test
    void shouldReturnWeatherData() {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"temperature\": 25, \"condition\": \"晴天\", \"humidity\": 60}")
                .addHeader("Content-Type", "application/json"));

        ToolResult result = weatherTool.execute(Map.of("city", "Beijing"));
        assertTrue(result.success());
        assertNotNull(result.data());
    }

    @Test
    void shouldHandleMissingCity() {
        ToolResult result = weatherTool.execute(Map.of());
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("city"));
    }

    @Test
    void shouldHandleApiFailure() {
        mockServer.enqueue(new MockResponse().setResponseCode(500));

        ToolResult result = weatherTool.execute(Map.of("city", "Beijing"));
        assertFalse(result.success());
    }
}
