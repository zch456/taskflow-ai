package com.taskflow.config;

import com.taskflow.service.LLMClient;
import com.taskflow.tool.registry.ToolManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    private final TaskFlowProperties properties;

    public AppConfig(TaskFlowProperties properties) {
        this.properties = properties;
    }

    @Bean
    public ToolManager toolManager() {
        return new ToolManager();
    }

    @Bean
    public LLMClient llmClient() {
        TaskFlowProperties.Llm llm = properties.getLlm();
        return new LLMClient(
                llm.getApiBaseUrl(),
                llm.getApiKey(),
                llm.getModel(),
                llm.getTemperature(),
                llm.getMaxTokens()
        );
    }
}
