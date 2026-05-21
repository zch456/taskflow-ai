package com.taskflow.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class LLMClientTest {

    private MockWebServer mockServer;
    private LLMClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        client = new LLMClient(baseUrl, "test-api-key", "gpt-4o", 0.7, 2000);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Test
    void shouldCallChatApiAndReturnContent() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody("""
                        {
                          "choices": [{
                            "message": {
                              "role": "assistant",
                              "content": "Hello, I am an AI assistant"
                            }
                          }]
                        }
                        """)
                .addHeader("Content-Type", "application/json"));

        String response = client.chat("Hi, who are you?");

        assertEquals("Hello, I am an AI assistant", response);

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("POST", request.getMethod());
        assertTrue(request.getBody().readUtf8().contains("Hi, who are you?"));
    }

    @Test
    void shouldReturnStructuredOutputAsJson() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody("""
                        {
                          "choices": [{
                            "message": {
                              "role": "assistant",
                              "content": "{\\"steps\\": [{\\"tool\\": \\"weather\\"}]}"
                            }
                          }]
                        }
                        """)
                .addHeader("Content-Type", "application/json"));

        String response = client.chat("Plan this task");

        assertTrue(response.contains("weather"));
    }

    @Test
    void shouldHandleApiErrorGracefully() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\": \"Internal server error\"}"));

        String response = client.chat("test prompt");
        assertTrue(response.startsWith("Error:"));
    }

    @Test
    void shouldHandleNetworkTimeout() {
        // Close server to simulate network issue
        try {
            mockServer.shutdown();
        } catch (IOException ignored) {}

        String response = client.chat("test prompt");
        assertTrue(response.startsWith("Error:"));
    }

    @Test
    void shouldIncludeApiKeyInHeaders() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"choices\": [{\"message\": {\"content\": \"ok\"}}]}")
                .addHeader("Content-Type", "application/json"));

        client.chat("test");

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("Bearer test-api-key", request.getHeader("Authorization"));
    }
}
