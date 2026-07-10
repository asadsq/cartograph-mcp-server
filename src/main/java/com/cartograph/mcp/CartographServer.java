/*
 * CartographServer.java
 * ---------------------
 * Purpose (plain English): Assembles the MCP server — it declares who we are, what we
 * can do, and which tools an AI agent is allowed to call. Every tool Cartograph offers
 * gets registered here before the server starts listening.
 */
package com.cartograph.mcp;

import com.cartograph.mcp.tools.PingTool;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public final class CartographServer {

    private static final String SERVER_NAME = "cartograph";
    private static final String SERVER_VERSION = "0.1.0";

    private CartographServer() {
        // Factory holder; never instantiated.
    }

    /**
     * Builds a Cartograph server that speaks MCP over the given streams. Callers pass
     * the process's own stdin/stdout in production; tests can pass pipes instead.
     */
    public static McpSyncServer create(InputStream in, OutputStream out) {
        var transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper(), in, out);

        return McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(List.of(PingTool.specification()))
                .build();
    }
}
