package com.taskflow.tool.impl;

import com.taskflow.tool.ParameterSchema;
import com.taskflow.tool.Tool;
import com.taskflow.tool.ToolResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DatabaseTool implements Tool {

    private final DataSource dataSource;

    public DatabaseTool(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getName() {
        return "database";
    }

    @Override
    public String getDescription() {
        return "执行SQL查询（仅支持SELECT），使用参数化查询，返回查询结果集";
    }

    @Override
    public Map<String, ParameterSchema> getInputSchema() {
        Map<String, ParameterSchema> schema = new LinkedHashMap<>();
        schema.put("sql", new ParameterSchema("string", "SQL SELECT查询语句，使用?作为参数占位符", true));
        schema.put("params", new ParameterSchema("array", "查询参数列表，按顺序对应SQL中的?占位符", false));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        long start = System.currentTimeMillis();
        String sql = (String) params.get("sql");

        if (sql == null || sql.isBlank()) {
            return ToolResult.failure("sql 参数不能为空", System.currentTimeMillis() - start);
        }

        String trimmedSql = sql.trim().toUpperCase();
        boolean isSelect = trimmedSql.startsWith("SELECT") || trimmedSql.startsWith("WITH");
        if (!isSelect) {
            return ToolResult.failure("仅允许执行 SELECT 查询", System.currentTimeMillis() - start);
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            Object paramObj = params.get("params");
            if (paramObj instanceof List<?> paramList) {
                for (int i = 0; i < paramList.size(); i++) {
                    stmt.setObject(i + 1, paramList.get(i));
                }
            }

            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                List<Map<String, Object>> rows = new ArrayList<>();

                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    rows.add(row);
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("columns", colCount);
                result.put("rows", rows);
                result.put("rowCount", rows.size());

                return ToolResult.success(result, System.currentTimeMillis() - start);
            }
        } catch (SQLException e) {
            return ToolResult.failure("数据库查询错误: " + e.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }
}
