package com.taskflow.tool.impl;

import com.taskflow.tool.ParameterSchema;
import com.taskflow.tool.Tool;
import com.taskflow.tool.ToolResult;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

public class CalculatorTool implements Tool {

    @Override
    public String getName() {
        return "calculator";
    }

    @Override
    public String getDescription() {
        return "执行数学表达式计算，支持 + - * / 等基本运算";
    }

    @Override
    public Map<String, ParameterSchema> getInputSchema() {
        Map<String, ParameterSchema> schema = new LinkedHashMap<>();
        schema.put("expression", new ParameterSchema("string", "数学表达式，如 2 + 3 * 4", true));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        long start = System.currentTimeMillis();
        String expressionStr = (String) params.get("expression");

        if (expressionStr == null || expressionStr.isBlank()) {
            return ToolResult.failure("expression 参数不能为空",
                    System.currentTimeMillis() - start);
        }

        try {
            Expression exp = new ExpressionBuilder(expressionStr).build();
            double result = exp.evaluate();
            return ToolResult.success(String.valueOf(result), System.currentTimeMillis() - start);
        } catch (IllegalArgumentException e) {
            return ToolResult.failure("表达式语法错误: " + e.getMessage(),
                    System.currentTimeMillis() - start);
        } catch (ArithmeticException e) {
            return ToolResult.failure("算术错误: " + e.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }
}
