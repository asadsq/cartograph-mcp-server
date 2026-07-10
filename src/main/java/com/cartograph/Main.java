/*
 * Main.java
 * ---------
 * Purpose (plain English): The starting point of the program. It boots the Cartograph
 * MCP server so an AI agent can talk to it over stdin/stdout, then waits. When the agent
 * disconnects (or the process is asked to stop) it shuts the server down cleanly instead
 * of leaving it running forever.
 */
package com.cartograph;

import com.cartograph.mcp.CartographServer;
import io.modelcontextprotocol.server.McpSyncServer;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

public final class Main {

    private Main() {
        // Entry-point holder; never instantiated.
    }

    public static void main(String[] args) throws InterruptedException {
        // stdout is the MCP wire protocol. Anything printed there that isn't JSON-RPC
        // corrupts the stream, so all human-readable logging must go to stderr.
        System.err.println("Cartograph MCP server starting on stdio...");

        CountDownLatch shutdown = new CountDownLatch(1);
        InputStream stdin = closesOnEndOfInput(System.in, shutdown);

        McpSyncServer server = CartographServer.create(stdin, System.out);

        Runtime.getRuntime().addShutdownHook(new Thread(shutdown::countDown));

        System.err.println("Cartograph MCP server ready.");
        shutdown.await();

        System.err.println("Cartograph MCP server shutting down.");
        server.closeGracefully();
    }

    /**
     * Wraps stdin so that reaching end-of-input — which is how an MCP client signals it
     * has disconnected — releases the latch holding {@code main} open. Without this the
     * JVM would linger after the client goes away.
     */
    private static InputStream closesOnEndOfInput(InputStream delegate, CountDownLatch shutdown) {
        return new FilterInputStream(delegate) {
            @Override
            public int read() throws IOException {
                return signalIfEof(super.read());
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                return signalIfEof(super.read(buffer, offset, length));
            }

            private int signalIfEof(int bytesRead) {
                if (bytesRead == -1) {
                    shutdown.countDown();
                }
                return bytesRead;
            }
        };
    }
}
