package com.taskflow.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.tool.ParameterSchema;
import com.taskflow.tool.Tool;
import com.taskflow.tool.ToolResult;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SearchTool implements Tool {

    private static final ObjectMapper mapper = new ObjectMapper();
    private final String apiBaseUrl;
    private final String apiKey;
    private final OkHttpClient httpClient;

    public SearchTool(String apiBaseUrl, String apiKey) {
        this.apiBaseUrl = apiBaseUrl;
        this.apiKey = apiKey;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String getName() {
        return "search";
    }

    @Override
    public String getDescription() {
        return "在互联网上搜索信息，返回相关结果列表";
    }

    @Override
    public Map<String, ParameterSchema> getInputSchema() {
        Map<String, ParameterSchema> schema = new LinkedHashMap<>();
        schema.put("query", new ParameterSchema("string", "搜索关键词", true));
        schema.put("resultCount", new ParameterSchema("int", "返回结果数量，默认5", false));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        long start = System.currentTimeMillis();
        String query = (String) params.get("query");
        Object countObj = params.get("resultCount");
        int count = countObj instanceof Number n ? n.intValue() : 5;

        if (query == null || query.isBlank()) {
            return ToolResult.failure("query 参数不能为空",
                    System.currentTimeMillis() - start);
        }

        try {
            HttpUrl url = HttpUrl.parse(apiBaseUrl);
            if (url == null) {
                return ToolResult.failure("搜索API URL无效",
                        System.currentTimeMillis() - start);
            }
            url = url.newBuilder()
                    .addQueryParameter("q", query)
                    .addQueryParameter("count", String.valueOf(count))
                    .addQueryParameter("key", apiKey)
                    .build();
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return ToolResult.failure("搜索API调用失败: HTTP " + response.code(),
                            System.currentTimeMillis() - start);
                }
                String body = response.body() != null ? response.body().string() : "{}";
                Object data = mapper.readValue(body, Object.class);
                return ToolResult.success(data, System.currentTimeMillis() - start);
            }
        } catch (IOException e) {
            return ToolResult.failure("搜索API调用异常: " + e.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }
}
