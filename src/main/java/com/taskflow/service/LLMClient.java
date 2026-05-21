package com.taskflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LLMClient {

    private static final MediaType JSON = MediaType.get("application/json");
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String baseUrl;  // normalized, without trailing slash
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final OkHttpClient httpClient;

    public LLMClient(String baseUrl, String apiKey, String model,
                     double temperature, int maxTokens) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/$", "") : null;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public String chat(String prompt) {
        if (baseUrl == null) {
            return "Error: LLM API base URL is not configured";
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", temperature,
                    "max_tokens", maxTokens
            );

            String jsonBody = mapper.writeValueAsString(body);
            Request request = new Request.Builder()
                    .url(baseUrl + "/v1/chat/completions")
                    .post(RequestBody.create(jsonBody, JSON))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return "Error: API returned status " + response.code();
                }
                String responseBody = response.body() != null
                        ? response.body().string() : "{}";
                JsonNode root = mapper.readTree(responseBody);
                return root.path("choices").get(0)
                        .path("message").path("content").asText();
            }
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }
}
