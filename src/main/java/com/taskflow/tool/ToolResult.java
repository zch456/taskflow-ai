package com.taskflow.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tool 执行结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {
    
    private boolean success;            // 是否成功
    private String output;              // 输出内容
    private long executionTimeMs;       // 执行耗时
    private String errorMessage;        // 错误信息
    private Object rawData;             // 原始数据
    
    /**
     * 创建成功的结果
     */
    public static ToolResult success(String output, long executionTimeMs) {
        ToolResult result = new ToolResult();
        result.setSuccess(true);
        result.setOutput(output);
        result.setExecutionTimeMs(executionTimeMs);
        return result;
    }
    
    /**
     * 创建失败的结果
     */
    public static ToolResult failure(String errorMessage) {
        ToolResult result = new ToolResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }
}
