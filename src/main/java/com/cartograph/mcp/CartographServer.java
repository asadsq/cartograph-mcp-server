/*
 * CartographServer.java
 * ---------------------
 * Purpose (plain English): Assembles the MCP server — it declares who we are, what we
 * can do, and which tools an AI agent is allowed to call. Every tool Cartograph offers
 * gets registered here before the server starts listening.
 */
package com.cartograph.mcp;

import com.cartograph.index.GraphStore;
import com.cartograph.mcp.tools.GetDependenciesTool;
import com.cartograph.mcp.tools.GetDependentsTool;
import com.cartograph.mcp.tools.IndexRepoTool;
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
    private static final String SERVER_VERSION = "0.2.0";

    /**
     * Guidance the AI agent reads once, when it connects. It covers the one rule that isn't
     * obvious from the tool list — index before querying — and asks the agent to tell the
     * user what Cartograph actually did, so the work is visible rather than magic.
     */
    private static final String INSTRUCTIONS = """
            Always call `index_repo` first before any query tool. Graph queries operate on the \
            most recently indexed repository. When answering the user, briefly say in plain, \
            friendly language what Cartograph checked to get the result (e.g. 'I traced \
            everything that depends on this file') so the tool's work is visible — without jargon.""";

    private CartographServer() {
        // Factory holder; never instantiated.
    }

    /**
     * Builds a Cartograph server that speaks MCP over the given streams. Callers pass
     * the process's own stdin/stdout in production; tests can pass pipes instead.
     */
    public static McpSyncServer create(InputStream in, OutputStream out) {
        var transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper(), in, out);

        // One store shared by every tool: index_repo fills it, the query tools read it. This
        // is what lets an agent index once and then ask many questions cheaply.
        GraphStore store = new GraphStore();

        return McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .instructions(INSTRUCTIONS)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(List.of(
                        PingTool.specification(),
                        IndexRepoTool.specification(store),
                        GetDependenciesTool.specification(store),
                        GetDependentsTool.specification(store)))
                .build();
    }
}
