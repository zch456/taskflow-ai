package com.taskflow.tool.impl;

import com.taskflow.tool.ParameterSchema;
import com.taskflow.tool.Tool;
import com.taskflow.tool.ToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileOperationTool implements Tool {

    private final Path whitelistDir;

    public FileOperationTool(String whitelistDir) {
        this.whitelistDir = Path.of(whitelistDir).toAbsolutePath().normalize();
    }

    @Override
    public String getName() {
        return "file_operation";
    }

    @Override
    public String getDescription() {
        return "在指定白名单目录内执行文件操作（读/写/列表/删除），路径受白名单限制";
    }

    @Override
    public Map<String, ParameterSchema> getInputSchema() {
        Map<String, ParameterSchema> schema = new LinkedHashMap<>();
        schema.put("operation", new ParameterSchema("string", "操作类型: read, write, list, delete", true));
        schema.put("path", new ParameterSchema("string", "文件路径（相对于白名单目录）", true));
        schema.put("content", new ParameterSchema("string", "要写入的文件内容（仅write操作需要）", false));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        long start = System.currentTimeMillis();
        String operation = (String) params.get("operation");
        String relativePath = (String) params.get("path");

        if (operation == null || operation.isBlank()) {
            return ToolResult.failure("operation 参数不能为空", System.currentTimeMillis() - start);
        }
        if (relativePath == null || relativePath.isBlank()) {
            return ToolResult.failure("path 参数不能为空", System.currentTimeMillis() - start);
        }

        try {
            Path targetPath = whitelistDir.resolve(relativePath).toAbsolutePath().normalize();
            if (!targetPath.startsWith(whitelistDir)) {
                return ToolResult.failure("路径超出白名单目录范围",
                        System.currentTimeMillis() - start);
            }

            return switch (operation.toLowerCase()) {
                case "read" -> handleRead(targetPath, start);
                case "write" -> handleWrite(targetPath, (String) params.get("content"), start);
                case "list" -> handleList(targetPath, start);
                case "delete" -> handleDelete(targetPath, start);
                default -> ToolResult.failure("不支持的操作类型: " + operation,
                        System.currentTimeMillis() - start);
            };
        } catch (IOException e) {
            return ToolResult.failure("文件操作异常: " + e.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }

    private ToolResult handleRead(Path path, long start) throws IOException {
        if (!Files.exists(path)) {
            return ToolResult.failure("文件不存在: " + path.getFileName(),
                    System.currentTimeMillis() - start);
        }
        if (!Files.isRegularFile(path)) {
            return ToolResult.failure("路径不是普通文件: " + path.getFileName(),
                    System.currentTimeMillis() - start);
        }
        String content = Files.readString(path);
        return ToolResult.success(content, System.currentTimeMillis() - start);
    }

    private ToolResult handleWrite(Path path, String content, long start) throws IOException {
        if (content == null) {
            return ToolResult.failure("content 参数不能为空",
                    System.currentTimeMillis() - start);
        }
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return ToolResult.success("文件写入成功: " + path.getFileName(),
                System.currentTimeMillis() - start);
    }

    private ToolResult handleList(Path path, long start) throws IOException {
        if (!Files.exists(path)) {
            return ToolResult.failure("目录不存在: " + path.getFileName(),
                    System.currentTimeMillis() - start);
        }
        if (!Files.isDirectory(path)) {
            return ToolResult.failure("路径不是目录: " + path.getFileName(),
                    System.currentTimeMillis() - start);
        }
        try (Stream<Path> entries = Files.list(path)) {
            List<String> fileNames = entries
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
            return ToolResult.success(fileNames, System.currentTimeMillis() - start);
        }
    }

    private ToolResult handleDelete(Path path, long start) throws IOException {
        if (!Files.exists(path)) {
            return ToolResult.failure("文件不存在: " + path.getFileName(),
                    System.currentTimeMillis() - start);
        }
        Files.delete(path);
        return ToolResult.success("文件删除成功: " + path.getFileName(),
                System.currentTimeMillis() - start);
    }
}
