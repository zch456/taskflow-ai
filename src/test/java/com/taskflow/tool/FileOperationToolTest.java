package com.taskflow.tool;

import com.taskflow.tool.impl.FileOperationTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileOperationToolTest {

    @TempDir
    Path tempDir;

    private FileOperationTool tool;

    @BeforeEach
    void setUp() {
        tool = new FileOperationTool(tempDir.toString());
    }

    @Test
    void shouldReturnCorrectName() {
        assertEquals("file_operation", tool.getName());
    }

    @Test
    void shouldHaveDescription() {
        assertNotNull(tool.getDescription());
        assertFalse(tool.getDescription().isEmpty());
    }

    @Test
    void shouldHaveInputSchema() {
        Map<String, ParameterSchema> schema = tool.getInputSchema();
        assertTrue(schema.containsKey("operation"));
        assertTrue(schema.get("operation").required());
        assertTrue(schema.containsKey("path"));
        assertTrue(schema.containsKey("content"));
    }

    @Test
    void shouldWriteAndReadFile() throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello, World!");

        ToolResult result = tool.execute(
                Map.of("operation", "read", "path", "test.txt"));
        assertTrue(result.success());
        assertEquals("Hello, World!", result.data());
    }

    @Test
    void shouldWriteNewFile() {
        ToolResult result = tool.execute(
                Map.of("operation", "write", "path", "output.txt", "content", "new content"));
        assertTrue(result.success());
        assertTrue(result.data().toString().contains("写入成功"));

        ToolResult readResult = tool.execute(
                Map.of("operation", "read", "path", "output.txt"));
        assertEquals("new content", readResult.data());
    }

    @Test
    void shouldListDirectoryContents() throws IOException {
        Files.createFile(tempDir.resolve("a.txt"));
        Files.createFile(tempDir.resolve("b.txt"));
        Files.createDirectory(tempDir.resolve("subdir"));

        ToolResult result = tool.execute(
                Map.of("operation", "list", "path", "."));
        assertTrue(result.success());

        @SuppressWarnings("unchecked")
        List<String> files = (List<String>) result.data();
        assertTrue(files.contains("a.txt"));
        assertTrue(files.contains("b.txt"));
        assertTrue(files.contains("subdir"));
    }

    @Test
    void shouldDeleteFile() throws IOException {
        Path testFile = tempDir.resolve("to_delete.txt");
        Files.writeString(testFile, "delete me");

        ToolResult result = tool.execute(
                Map.of("operation", "delete", "path", "to_delete.txt"));
        assertTrue(result.success());
        assertTrue(result.data().toString().contains("删除成功"));
        assertFalse(Files.exists(testFile));
    }

    @Test
    void shouldRejectPathOutsideWhitelist() {
        ToolResult result = tool.execute(
                Map.of("operation", "read", "path", "../etc/passwd"));
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("白名单"));
    }

    @Test
    void shouldRejectEmptyOperation() {
        ToolResult result = tool.execute(
                Map.of("operation", "", "path", "test.txt"));
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("不能为空"));
    }

    @Test
    void shouldRejectUnknownOperation() {
        ToolResult result = tool.execute(
                Map.of("operation", "execute", "path", "test.txt"));
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("不支持"));
    }
}
