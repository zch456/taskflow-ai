package com.taskflow.config;

import com.taskflow.tool.impl.CalculatorTool;
import com.taskflow.tool.impl.DatabaseTool;
import com.taskflow.tool.impl.FileOperationTool;
import com.taskflow.tool.impl.JsonTool;
import com.taskflow.tool.impl.SearchTool;
import com.taskflow.tool.impl.WeatherTool;
import com.taskflow.tool.registry.ToolManager;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class ToolRegistration {

    private final ToolManager toolManager;
    private final TaskFlowProperties properties;
    private final DataSource dataSource;

    public ToolRegistration(ToolManager toolManager, TaskFlowProperties properties,
                            DataSource dataSource) {
        this.toolManager = toolManager;
        this.properties = properties;
        this.dataSource = dataSource;
    }

    @PostConstruct
    void registerTools() {
        toolManager.registerTool(new CalculatorTool());
        toolManager.registerTool(new JsonTool());
        toolManager.registerTool(new DatabaseTool(dataSource));

        TaskFlowProperties.FileOperation fileOpConfig = properties.getFileOperation();
        if (fileOpConfig != null) {
            toolManager.registerTool(
                    new FileOperationTool(fileOpConfig.getWhitelistDir()));
        }

        TaskFlowProperties.Tool weatherConfig = properties.getWeather();
        if (weatherConfig != null && weatherConfig.getApiBaseUrl() != null) {
            toolManager.registerTool(new WeatherTool(
                    weatherConfig.getApiBaseUrl(), weatherConfig.getApiKey()));
        }

        TaskFlowProperties.Tool searchConfig = properties.getSearch();
        if (searchConfig != null && searchConfig.getApiBaseUrl() != null) {
            toolManager.registerTool(new SearchTool(
                    searchConfig.getApiBaseUrl(), searchConfig.getApiKey()));
        }
    }
}
