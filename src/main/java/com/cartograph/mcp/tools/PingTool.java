/*
 * PingTool.java
 * -------------
 * Purpose (plain English): A tiny "is anyone home?" tool. An AI agent calls it to confirm
 * Cartograph is running and reachable before trying anything real. It takes no input and
 * replies with a friendly hello.
 */
package com.cartograph.mcp.tools;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

public final class PingTool {

    private static final String NAME = "ping";

    private static final String DESCRIPTION =
            "Health check. Confirms the Cartograph server is running and reachable. "
                    + "Takes no arguments and returns a short greeting.";

    /** No arguments: an object schema with no properties. */
    private static final Map<String, Object> INPUT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(),
            "additionalProperties", false);

    private PingTool() {
        // Tool definition holder; never instantiated.
    }

    public static McpServerFeatures.SyncToolSpecification specification() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(NAME)
                .title("Ping")
                .description(DESCRIPTION)
                .inputSchema(INPUT_SCHEMA)
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> McpSchema.CallToolResult.builder()
                        .addTextContent("Hello from Cartograph — a mapmaker for your codebase.")
                        .build())
                .build();
    }
}
