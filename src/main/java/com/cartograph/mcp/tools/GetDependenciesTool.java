/*
 * GetDependenciesTool.java
 * ------------------------
 * Purpose (plain English): Answers "what does this class rely on?" — the things it would stop
 * working without. Set a depth above 1 to also see what those things rely on in turn.
 */
package com.cartograph.mcp.tools;

import com.cartograph.graph.Neighbour;
import com.cartograph.index.GraphStore;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GetDependenciesTool {

    private static final String NAME = "get_dependencies";

    private static final String DESCRIPTION =
            "List what a class or interface depends on — the code it uses and would break "
                    + "without. Use depth 1 for direct dependencies only, or a higher depth to "
                    + "follow the chain onwards. Requires index_repo to have been run first.";

    private static final int DEFAULT_DEPTH = 1;

    private GetDependenciesTool() {
        // Tool definition holder; never instantiated.
    }

    public static McpServerFeatures.SyncToolSpecification specification(GraphStore store) {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(NAME)
                .title("Get dependencies")
                .description(DESCRIPTION)
                .inputSchema(GraphLookup.symbolAndDepthSchema(
                        "The class or interface to inspect. Short name (UserService) or full name "
                                + "(com.app.UserService); use the full name if the short one is ambiguous.",
                        "How many steps of dependencies to follow. 1 means direct dependencies only."))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> handle(store, request.arguments()))
                .build();
    }

    private static McpSchema.CallToolResult handle(GraphStore store, Map<String, Object> arguments) {
        GraphLookup.Target target = GraphLookup.locate(store, arguments);
        if (target.failed()) {
            return target.failure();
        }

        int depth = Math.max(1, ToolReply.intArgument(arguments, "depth", DEFAULT_DEPTH));
        List<Neighbour> found = target.repository().graph().dependencies(target.symbolId(), depth);

        long direct = found.stream().filter(n -> n.distance() == 1).count();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("symbol", target.symbolId());
        data.put("depth", depth);
        data.put("total", found.size());
        data.put("direct", direct);
        data.put("dependencies", GraphLookup.describe(found));

        return ToolReply.success(methodSentence(target.symbolId(), depth, found.size(), direct), data);
    }

    /** The plain-English line the agent repeats to the user. No jargon, and always a number. */
    private static String methodSentence(String symbol, int depth, int total, long direct) {
        String name = shortName(symbol);
        if (total == 0) {
            return "Checked what %s relies on — it doesn't depend on anything else in this codebase.".formatted(name);
        }
        if (depth == 1) {
            return "Checked what %s relies on directly — it depends on %d other %s here."
                    .formatted(name, total, total == 1 ? "file" : "files");
        }
        return "Followed what %s relies on, up to %d steps out — %d directly, %d in total."
                .formatted(name, depth, direct, total);
    }

    private static String shortName(String id) {
        int lastDot = id.lastIndexOf('.');
        return lastDot < 0 ? id : id.substring(lastDot + 1);
    }
}
