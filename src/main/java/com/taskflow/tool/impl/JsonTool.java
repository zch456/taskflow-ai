package com.taskflow.tool.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.taskflow.tool.ParameterSchema;
import com.taskflow.tool.Tool;
import com.taskflow.tool.ToolResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonTool implements Tool {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getName() {
        return "json";
    }

    @Override
    public String getDescription() {
        return "JSON数据处理：解析验证、属性查询（点分隔路径）、结构转换";
    }

    @Override
    public Map<String, ParameterSchema> getInputSchema() {
        Map<String, ParameterSchema> schema = new LinkedHashMap<>();
        schema.put("operation", new ParameterSchema("string", "操作类型: parse, query, transform", true));
        schema.put("json", new ParameterSchema("string", "JSON字符串", true));
        schema.put("expression", new ParameterSchema("string", "操作表达式。query: 点分隔属性路径如 data.name；transform: 新属性名映射如 name:fullName", false));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        long start = System.currentTimeMillis();
        String operation = (String) params.get("operation");
        String json = (String) params.get("json");

        if (operation == null || operation.isBlank()) {
            return ToolResult.failure("operation 参数不能为空",
                    System.currentTimeMillis() - start);
        }
        if (json == null || json.isBlank()) {
            return ToolResult.failure("json 参数不能为空",
                    System.currentTimeMillis() - start);
        }

        try {
            return switch (operation.toLowerCase()) {
                case "parse" -> handleParse(json, start);
                case "query" -> handleQuery(json, (String) params.get("expression"), start);
                case "transform" -> handleTransform(json, (String) params.get("expression"), start);
                default -> ToolResult.failure("不支持的操作类型: " + operation,
                        System.currentTimeMillis() - start);
            };
        } catch (JsonProcessingException e) {
            return ToolResult.failure("JSON解析错误: " + e.getMessage(),
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            return ToolResult.failure("JSON处理错误: " + e.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }

    private ToolResult handleParse(String json, long start) throws JsonProcessingException {
        JsonNode node = mapper.readTree(json);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", true);
        result.put("type", node.getNodeType().name());
        if (node.isObject()) {
            result.put("fieldCount", node.size());
        } else if (node.isArray()) {
            result.put("elementCount", node.size());
        }
        return ToolResult.success(result, System.currentTimeMillis() - start);
    }

    private ToolResult handleQuery(String json, String expression, long start)
            throws JsonProcessingException {
        if (expression == null || expression.isBlank()) {
            return ToolResult.failure("expression 参数不能为空（query操作需要指定属性路径）",
                    System.currentTimeMillis() - start);
        }

        JsonNode node = mapper.readTree(json);
        String[] parts = expression.split("\\.");
        JsonNode current = node;

        for (String part : parts) {
            if (current == null) break;
            part = part.trim();
            if (current.isArray()) {
                try {
                    int index = Integer.parseInt(part);
                    current = current.get(index);
                } catch (NumberFormatException e) {
                    List<Object> collected = new ArrayList<>();
                    for (JsonNode item : current) {
                        JsonNode child = item.get(part);
                        if (child != null) collected.add(jsonNodeToValue(child));
                    }
                    return ToolResult.success(collected,
                            System.currentTimeMillis() - start);
                }
            } else {
                current = current.get(part);
            }
        }

        if (current == null) {
            return ToolResult.failure("路径未找到: " + expression,
                    System.currentTimeMillis() - start);
        }

        return ToolResult.success(jsonNodeToValue(current),
                System.currentTimeMillis() - start);
    }

    @SuppressWarnings("unchecked")
    private ToolResult handleTransform(String json, String expression, long start)
            throws JsonProcessingException {
        JsonNode node = mapper.readTree(json);

        if (expression != null && !expression.isBlank()) {
            Map<String, String> mapping = parseMapping(expression);
            if (node.isArray()) {
                ArrayNode transformed = mapper.createArrayNode();
                for (JsonNode item : node) {
                    transformed.add(applyRename((ObjectNode) item, mapping));
                }
                return ToolResult.success(mapper.treeToValue(transformed, Object.class),
                        System.currentTimeMillis() - start);
            } else if (node.isObject()) {
                ObjectNode transformed = applyRename((ObjectNode) node, mapping);
                return ToolResult.success(mapper.treeToValue(transformed, Object.class),
                        System.currentTimeMillis() - start);
            }
        }

        return ToolResult.success(mapper.treeToValue(node, Object.class),
                System.currentTimeMillis() - start);
    }

    private Map<String, String> parseMapping(String expression) {
        Map<String, String> mapping = new LinkedHashMap<>();
        String[] pairs = expression.split(",");
        for (String pair : pairs) {
            String[] kv = pair.trim().split(":");
            if (kv.length == 2) {
                mapping.put(kv[0].trim(), kv[1].trim());
            }
        }
        return mapping;
    }

    private ObjectNode applyRename(ObjectNode node, Map<String, String> mapping) {
        ObjectNode renamed = mapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String newName = mapping.getOrDefault(field.getKey(), field.getKey());
            renamed.set(newName, field.getValue());
        }
        return renamed;
    }

    private Object jsonNodeToValue(JsonNode node) {
        switch (node.getNodeType()) {
            case NULL: return null;
            case BOOLEAN: return node.booleanValue();
            case NUMBER:
                if (node.isInt()) return node.intValue();
                if (node.isLong()) return node.longValue();
                return node.doubleValue();
            case STRING: return node.textValue();
            case ARRAY: {
                List<Object> list = new ArrayList<>();
                node.forEach(n -> list.add(jsonNodeToValue(n)));
                return list;
            }
            case OBJECT: {
                Map<String, Object> map = new LinkedHashMap<>();
                node.fields().forEachRemaining(
                        e -> map.put(e.getKey(), jsonNodeToValue(e.getValue())));
                return map;
            }
            default: return node.asText();
        }
    }
}
