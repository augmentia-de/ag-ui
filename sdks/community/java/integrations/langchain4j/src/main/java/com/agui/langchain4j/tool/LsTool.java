package com.agui.langchain4j.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LsTool {
    private static final Logger log = LoggerFactory.getLogger(LsTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Path cwd;

    private LsTool(Path cwd) {
        this.cwd = cwd;
    }

    public static ExecutableTool create(Path cwd) {
        var tool = new LsTool(cwd);
        var spec = ToolSpecification.builder()
                .name("ls")
                .description("List directory contents. Use path for specific directory, recursive for subdirectories.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "Directory to list (default: current directory)")
                        .addBooleanProperty("recursive", "List recursively (default: false)")
                        .addIntegerProperty("depth", "Maximum depth for recursive listing (default: no limit)")
                        .addBooleanProperty("details", "Show file size and date (default: false)")
                        .build())
                .build();
        return new ExecutableTool(spec, tool::execute);
    }

    public String execute(String argumentsJson) {
        try {
            JsonNode root = objectMapper.readTree(argumentsJson);
            String pathStr = root.has("path") && !root.get("path").isNull()
                    ? root.get("path").asText() : null;
            boolean recursive = root.has("recursive") && root.get("recursive").asBoolean(false);
            boolean details = root.has("details") && root.get("details").asBoolean(false);
            Integer depth = root.has("depth") && !root.get("depth").isNull()
                    ? root.get("depth").asInt() : null;

            Path targetPath = pathStr != null ? resolve(pathStr) : cwd;

            if (!Files.exists(targetPath)) {
                return "{\"error\": \"Path does not exist: " + pathStr + "\"}";
            }
            if (!Files.isDirectory(targetPath)) {
                return "{\"error\": \"Path is not a directory: " + pathStr + "\"}";
            }

            var entries = new ArrayList<Path>();
            if (recursive) {
                int maxDepth = depth != null ? depth : Integer.MAX_VALUE;
                try (var stream = Files.walk(targetPath, maxDepth)) {
                    stream.skip(1).forEach(entries::add);
                }
            } else {
                try (var stream = Files.list(targetPath)) {
                    stream.forEach(entries::add);
                }
            }

            entries.sort(Comparator.comparing(Path::toString));

            var items = new ArrayList<Map<String, Object>>();
            for (var entry : entries) {
                var name = targetPath.relativize(entry).toString();
                var item = new LinkedHashMap<String, Object>();
                item.put("name", name);
                item.put("is_directory", Files.isDirectory(entry));
                if (details) {
                    try {
                        var attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                        item.put("size", attrs.size());
                        item.put("last_modified", attrs.lastModifiedTime().toString());
                    } catch (IOException ignored) {
                    }
                }
                items.add(item);
            }

            var result = new LinkedHashMap<String, Object>();
            result.put("path", targetPath.toString());
            result.put("entry_count", items.size());
            result.put("entries", items);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);

        } catch (Exception e) {
            log.error("ls tool execution failed", e);
            return "{\"error\": \"Failed to list directory: " + e.getMessage() + "\"}";
        }
    }

    private Path resolve(String path) {
        var p = Paths.get(path);
        return p.isAbsolute() ? p : cwd.resolve(p).normalize();
    }
}
