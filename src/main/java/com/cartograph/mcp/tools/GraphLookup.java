/*
 * GraphLookup.java
 * ----------------
 * Purpose (plain English): The shared groundwork behind every question tool — check that a
 * repository has actually been indexed, work out which type the user meant, and say something
 * helpful when the name is missing or matches more than one thing.
 */
package com.cartograph.mcp.tools;

import com.cartograph.graph.CodeGraph;
import com.cartograph.graph.Neighbour;
import com.cartograph.index.GraphStore;
import com.cartograph.index.IndexedRepository;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GraphLookup {

    /** How many candidate names to list back when a short name is ambiguous. */
    private static final int MAX_SUGGESTIONS = 10;

    private GraphLookup() {
        // Helper holder; never instantiated.
    }

    /**
     * The outcome of turning a user's symbol name into a graph node. Exactly one of
     * {@code symbolId} or {@code failure} is set.
     */
    record Target(IndexedRepository repository, String symbolId, McpSchema.CallToolResult failure) {

        boolean failed() {
            return failure != null;
        }
    }

    /**
     * Finds the one type the request is about. Ambiguity is reported rather than guessed at:
     * silently picking one of several same-named classes would give a confidently wrong
     * answer, which is worse than asking the agent to be specific.
     */
    static Target locate(GraphStore store, Map<String, Object> arguments) {
        IndexedRepository repository = store.mostRecent();
        if (repository == null) {
            return failed("No repository has been indexed yet. Run index_repo with the repository's path first.");
        }

        String symbol = ToolReply.stringArgument(arguments, "symbol");
        if (symbol == null) {
            return failed("Provide 'symbol' — the name of the class or interface to look at, "
                    + "either short (UserService) or full (com.app.UserService).");
        }

        CodeGraph graph = repository.graph();
        List<String> matches = graph.findTypes(symbol);

        if (matches.isEmpty()) {
            return failed("Couldn't find '%s' in %s. Check the spelling, or run index_repo again if the code has changed."
                    .formatted(symbol, repository.path()));
        }
        if (matches.size() > 1) {
            return failed("'%s' matches %d types in this repository. Ask again using the full name, one of: %s"
                    .formatted(symbol, matches.size(), String.join(", ", matches.subList(0, Math.min(matches.size(), MAX_SUGGESTIONS)))));
        }
        return new Target(repository, matches.get(0), null);
    }

    private static Target failed(String message) {
        return new Target(null, null, ToolReply.failure(message));
    }

    /** Turns graph results into plain JSON-friendly maps, grouped so distance stays visible. */
    static List<Map<String, Object>> describe(List<Neighbour> neighbours) {
        return neighbours.stream().map(GraphLookup::describe).toList();
    }

    private static Map<String, Object> describe(Neighbour neighbour) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("symbol", neighbour.id());
        entry.put("steps_away", neighbour.distance());
        if (neighbour.kind() != null) {
            entry.put("kind", neighbour.kind());
        }
        if (neighbour.file() != null) {
            entry.put("file", neighbour.file());
        }
        if (!neighbour.via().isEmpty()) {
            entry.put("connected_by", neighbour.via());
        }
        return entry;
    }

    /** The shared input schema for tools that take a symbol and an optional depth. */
    static Map<String, Object> symbolAndDepthSchema(String symbolDescription, String depthDescription) {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "symbol", Map.of(
                                "type", "string",
                                "description", symbolDescription),
                        "depth", Map.of(
                                "type", "integer",
                                "minimum", 1,
                                "maximum", 20,
                                "default", 1,
                                "description", depthDescription)),
                "required", List.of("symbol"),
                "additionalProperties", false);
    }
}
