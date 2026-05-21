package com.taskflow.tool.registry;

import com.taskflow.tool.Tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolManager {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public void registerTool(Tool tool) {
        tools.put(tool.getName(), tool);
    }

    public Tool getTool(String name) {
        Tool tool = tools.get(name);
        if (tool != null) {
            return tool;
        }
        // Fuzzy match: normalize names (lowercase, strip underscores) for comparison
        String normalized = name.toLowerCase().replace("_", "");
        for (Map.Entry<String, Tool> entry : tools.entrySet()) {
            String key = entry.getKey().toLowerCase().replace("_", "");
            if (key.equals(normalized) || key.contains(normalized) || normalized.contains(key)) {
                return entry.getValue();
            }
        }
        throw new IllegalArgumentException("Tool not found: " + name);
    }

    public List<Tool> listAvailableTools() {
        return new ArrayList<>(tools.values());
    }

    public List<String> getToolNames() {
        return new ArrayList<>(tools.keySet());
    }

    public Map<String, Tool> getAllTools() {
        return new LinkedHashMap<>(tools);
    }
}
