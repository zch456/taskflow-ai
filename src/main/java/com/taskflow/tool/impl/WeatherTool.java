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

public class WeatherTool implements Tool {

    private static final ObjectMapper mapper = new ObjectMapper();
    private final String apiBaseUrl;
    private final String apiKey;
    private final OkHttpClient httpClient;

    public WeatherTool(String apiBaseUrl, String apiKey) {
        this.apiBaseUrl = apiBaseUrl;
        this.apiKey = apiKey;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String getName() {
        return "weather";
    }

    @Override
    public String getDescription() {
        return "查询指定城市的天气信息，返回温度、天气状况、湿度等数据";
    }

    @Override
    public Map<String, ParameterSchema> getInputSchema() {
        Map<String, ParameterSchema> schema = new LinkedHashMap<>();
        schema.put("city", new ParameterSchema("string", "城市名称", true));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        long start = System.currentTimeMillis();
        String city = (String) params.get("city");

        if (city == null || city.isBlank()) {
            return ToolResult.failure("city 参数不能为空",
                    System.currentTimeMillis() - start);
        }

        try {
            HttpUrl url = HttpUrl.parse(apiBaseUrl);
            if (url == null) {
                return ToolResult.failure("天气API URL无效",
                        System.currentTimeMillis() - start);
            }
            url = url.newBuilder()
                    .addQueryParameter("q", city)
                    .addQueryParameter("key", apiKey)
                    .build();
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return ToolResult.failure("天气API调用失败: HTTP " + response.code(),
                            System.currentTimeMillis() - start);
                }
                String body = response.body() != null ? response.body().string() : "{}";
                Object data = mapper.readValue(body, Object.class);
                return ToolResult.success(data, System.currentTimeMillis() - start);
            }
        } catch (IOException e) {
            return ToolResult.failure("天气API调用异常: " + e.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }
}
