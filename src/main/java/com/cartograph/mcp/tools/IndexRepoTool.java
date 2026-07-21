/*
 * IndexRepoTool.java
 * ------------------
 * Purpose (plain English): The tool that reads a repository and builds its map. It must be
 * run before any question can be asked, and it reports honestly how much of the code it
 * managed to follow rather than implying it understood everything.
 */
package com.cartograph.mcp.tools;

import com.cartograph.index.GraphStore;
import com.cartograph.index.IndexedRepository;
import com.cartograph.index.RepositoryIndexer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IndexRepoTool {

    private static final String NAME = "index_repo";

    private static final String DESCRIPTION =
            "Parse a code repository and build its dependency graph. Call this FIRST — every "
                    + "other Cartograph tool answers questions about the most recently indexed "
                    + "repository. Returns a summary of what was mapped, including an honest "
                    + "coverage figure for how many references could be followed.";

    private static final Map<String, Object> INPUT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "path", Map.of(
                            "type", "string",
                            "description", "Absolute path to the repository's root folder."),
                    "languages", Map.of(
                            "type", "array",
                            "items", Map.of("type", "string", "enum", List.of("java")),
                            "description", "Limit indexing to these languages. Omit to auto-detect."),
                    "exclude", Map.of(
                            "type", "array",
                            "items", Map.of("type", "string"),
                            "description",
                            "Glob patterns to skip, relative to the repo root, e.g. '**/generated/**'. "
                                    + "Build output, .git and node_modules are always skipped.")),
            "required", List.of("path"),
            "additionalProperties", false);

    private IndexRepoTool() {
        // Tool definition holder; never instantiated.
    }

    public static McpServerFeatures.SyncToolSpecification specification(GraphStore store) {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(NAME)
                .title("Index repository")
                .description(DESCRIPTION)
                .inputSchema(INPUT_SCHEMA)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> handle(store, request.arguments()))
                .build();
    }

    private static McpSchema.CallToolResult handle(GraphStore store, Map<String, Object> arguments) {
        String rawPath = ToolReply.stringArgument(arguments, "path");
        if (rawPath == null) {
            return ToolReply.failure("Provide 'path' — the absolute path to the repository you want mapped.");
        }

        Path root;
        try {
            root = Path.of(rawPath).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            return ToolReply.failure("That path could not be read: " + rawPath);
        }
        if (!Files.isDirectory(root)) {
            return ToolReply.failure("No folder found at " + root + ". Give the path to the repository's root folder.");
        }

        List<String> languages = stringList(arguments, "languages");
        List<String> excludes = stringList(arguments, "exclude");

        IndexedRepository indexed;
        try {
            indexed = new RepositoryIndexer().index(root, languages, excludes);
        } catch (IOException e) {
            return ToolReply.failure("Could not index " + root + ": " + e.getMessage());
        }

        store.put(indexed);

        if (indexed.filesParsed() == 0) {
            return ToolReply.success(
                    "Looked through %s for code to map, but found no supported source files.".formatted(root),
                    Map.of("path", indexed.path(), "files_parsed", 0, "types", 0, "dependencies", 0));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", indexed.path());
        data.put("files_parsed", indexed.filesParsed());
        data.put("types", indexed.graph().typeCount());
        data.put("dependencies", indexed.graph().dependencyCount());
        data.put("languages", indexed.languages());
        data.put("elapsed_ms", indexed.elapsedMillis());
        data.put("coverage", coverageBlock(indexed));

        String method = "Read %d %s file%s and mapped how their %d types depend on each other, finding %d connections in %d ms."
                .formatted(
                        indexed.filesParsed(),
                        String.join("/", indexed.languages()),
                        indexed.filesParsed() == 1 ? "" : "s",
                        indexed.graph().typeCount(),
                        indexed.graph().dependencyCount(),
                        indexed.elapsedMillis());

        return ToolReply.success(method, data);
    }

    private static Map<String, Object> coverageBlock(IndexedRepository indexed) {
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("references_seen", indexed.coverage().total());
        coverage.put("matched_in_this_repo", indexed.coverage().internal());
        coverage.put("matched_to_outside_libraries", indexed.coverage().external());
        coverage.put("could_not_place", indexed.coverage().unresolved());
        coverage.put("percent_accounted_for", indexed.coverage().percentAccountedFor());
        coverage.put("note", indexed.coverage().plainSummary());
        if (!indexed.coverage().examples().isEmpty()) {
            coverage.put("examples_not_placed", indexed.coverage().examples());
        }
        return coverage;
    }

    /** Reads an optional array-of-strings argument, tolerating a single string too. */
    private static List<String> stringList(Map<String, Object> arguments, String name) {
        Object raw = arguments == null ? null : arguments.get(name);
        if (raw instanceof String single && !single.isBlank()) {
            return List.of(single.trim());
        }
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !item.toString().isBlank()) {
                values.add(item.toString().trim());
            }
        }
        return values;
    }
}
