/*
 * GetDependentsTool.java
 * ----------------------
 * Purpose (plain English): Answers the reverse question — "what would I break if I changed
 * this?". It finds everything that leans on the given class, which is the first thing worth
 * knowing before editing shared code.
 */
package com.cartograph.mcp.tools;

import com.cartograph.graph.Neighbour;
import com.cartograph.index.GraphStore;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GetDependentsTool {

    private static final String NAME = "get_dependents";

    private static final String DESCRIPTION =
            "List what depends ON a class or interface — the code that would be affected if you "
                    + "changed it. This is the reverse of get_dependencies and is the right tool "
                    + "for judging the risk of a change. Requires index_repo to have been run first.";

    private static final int DEFAULT_DEPTH = 1;

    private GetDependentsTool() {
        // Tool definition holder; never instantiated.
    }

    public static McpServerFeatures.SyncToolSpecification specification(GraphStore store) {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(NAME)
                .title("Get dependents")
                .description(DESCRIPTION)
                .inputSchema(GraphLookup.symbolAndDepthSchema(
                        "The class or interface to inspect. Short name (UserService) or full name "
                                + "(com.app.UserService); use the full name if the short one is ambiguous.",
                        "How many steps of dependents to follow. 1 means whatever uses it directly."))
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
        List<Neighbour> found = target.repository().graph().dependents(target.symbolId(), depth);

        long direct = found.stream().filter(n -> n.distance() == 1).count();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("symbol", target.symbolId());
        data.put("depth", depth);
        data.put("total", found.size());
        data.put("direct", direct);
        data.put("dependents", GraphLookup.describe(found));

        return ToolReply.success(methodSentence(target.symbolId(), depth, found.size(), direct), data);
    }

    /** The plain-English line the agent repeats to the user. No jargon, and always a number. */
    private static String methodSentence(String symbol, int depth, int total, long direct) {
        String name = shortName(symbol);
        if (total == 0) {
            return "Looked for anything that relies on %s — nothing else in this codebase uses it.".formatted(name);
        }
        if (depth == 1) {
            return "Looked for what relies on %s directly — %d %s use%s it."
                    .formatted(name, total, total == 1 ? "file" : "files", total == 1 ? "s" : "");
        }
        return "Traced what relies on %s, up to %d steps out — %d directly, %d that could be affected in total."
                .formatted(name, depth, direct, total);
    }

    private static String shortName(String id) {
        int lastDot = id.lastIndexOf('.');
        return lastDot < 0 ? id : id.substring(lastDot + 1);
    }
}
