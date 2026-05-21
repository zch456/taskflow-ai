package com.taskflow.tool;

import com.taskflow.tool.impl.DatabaseTool;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseToolTest {

    private DatabaseTool databaseTool;
    private JdbcDataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:testdb_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100), age INT)");
            stmt.execute("INSERT INTO users VALUES (1, 'Alice', 30)");
            stmt.execute("INSERT INTO users VALUES (2, 'Bob', 25)");
            stmt.execute("INSERT INTO users VALUES (3, 'Charlie', 35)");
        }

        databaseTool = new DatabaseTool(dataSource);
    }

    @Test
    void shouldReturnCorrectName() {
        assertEquals("database", databaseTool.getName());
    }

    @Test
    void shouldHaveDescription() {
        assertNotNull(databaseTool.getDescription());
        assertFalse(databaseTool.getDescription().isEmpty());
    }

    @Test
    void shouldHaveInputSchema() {
        Map<String, ParameterSchema> schema = databaseTool.getInputSchema();
        assertTrue(schema.containsKey("sql"));
        assertTrue(schema.get("sql").required());
        assertTrue(schema.containsKey("params"));
        assertFalse(schema.get("params").required());
    }

    @Test
    void shouldExecuteSelectQuery() {
        ToolResult result = databaseTool.execute(Map.of("sql", "SELECT * FROM users"));
        assertTrue(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();
        assertEquals(3, data.get("rowCount"));
        assertEquals(3, ((List<?>) data.get("rows")).size());
    }

    @Test
    void shouldExecuteSelectWithWhereClause() {
        ToolResult result = databaseTool.execute(
                Map.of("sql", "SELECT * FROM users WHERE age > ?", "params", List.of(28)));
        assertTrue(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();
        assertEquals(2, data.get("rowCount"));
    }

    @Test
    void shouldExecuteSelectWithMultipleParams() {
        ToolResult result = databaseTool.execute(
                Map.of("sql", "SELECT * FROM users WHERE age > ? AND name LIKE ?",
                        "params", List.of(20, "A%")));
        assertTrue(result.success());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();
        assertEquals(1, data.get("rowCount"));
    }

    @Test
    void shouldRejectNonSelectQuery() {
        ToolResult result = databaseTool.execute(Map.of("sql", "DROP TABLE users"));
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("仅允许执行 SELECT"));
    }

    @Test
    void shouldRejectInsertQuery() {
        ToolResult result = databaseTool.execute(
                Map.of("sql", "INSERT INTO users VALUES (4, 'Dave', 40)"));
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("仅允许执行 SELECT"));
    }

    @Test
    void shouldRejectEmptySql() {
        ToolResult result = databaseTool.execute(Map.of("sql", ""));
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("不能为空"));
    }

    @Test
    void shouldHandleInvalidSql() {
        ToolResult result = databaseTool.execute(
                Map.of("sql", "SELECT * FROM nonexistent_table"));
        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }
}
