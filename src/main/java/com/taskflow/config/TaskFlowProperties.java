package com.taskflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "taskflow")
public class TaskFlowProperties {

    private Agent agent = new Agent();
    private Llm llm = new Llm();
    private Auth auth = new Auth();
    private Tool weather = new Tool();
    private Tool search = new Tool();
    private FileOperation fileOperation = new FileOperation();

    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }
    public Llm getLlm() { return llm; }
    public void setLlm(Llm llm) { this.llm = llm; }
    public Auth getAuth() { return auth; }
    public void setAuth(Auth auth) { this.auth = auth; }
    public Tool getWeather() { return weather; }
    public void setWeather(Tool weather) { this.weather = weather; }
    public Tool getSearch() { return search; }
    public void setSearch(Tool search) { this.search = search; }
    public FileOperation getFileOperation() { return fileOperation; }
    public void setFileOperation(FileOperation fileOperation) { this.fileOperation = fileOperation; }

    public static class Agent {
        private int maxSteps = 10;
        private int timeoutSeconds = 30;
        private boolean cacheEnabled = false;
        private boolean idempotencyEnabled = false;

        public int getMaxSteps() { return maxSteps; }
        public void setMaxSteps(int maxSteps) { this.maxSteps = maxSteps; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public boolean isCacheEnabled() { return cacheEnabled; }
        public void setCacheEnabled(boolean cacheEnabled) { this.cacheEnabled = cacheEnabled; }
        public boolean isIdempotencyEnabled() { return idempotencyEnabled; }
        public void setIdempotencyEnabled(boolean idempotencyEnabled) { this.idempotencyEnabled = idempotencyEnabled; }
    }

    public static class Llm {
        private String provider = "openai";
        private String model = "gpt-4o";
        private double temperature = 0.7;
        private int maxTokens = 2000;
        private String apiKey;
        private String apiBaseUrl;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getApiBaseUrl() { return apiBaseUrl; }
        public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    }

    public static class Auth {
        private boolean enabled = false;
        private String headerName = "X-API-Key";
        private String apiKey;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getHeaderName() { return headerName; }
        public void setHeaderName(String headerName) { this.headerName = headerName; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    public static class Tool {
        private String apiKey;
        private String apiBaseUrl;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getApiBaseUrl() { return apiBaseUrl; }
        public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    }

    public static class FileOperation {
        private String whitelistDir = "/tmp/taskflow";

        public String getWhitelistDir() { return whitelistDir; }
        public void setWhitelistDir(String whitelistDir) { this.whitelistDir = whitelistDir; }
    }
}
