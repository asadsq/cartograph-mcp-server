/*
 * ToolReply.java
 * --------------
 * Purpose (plain English): Puts every tool's answer into the same shape before sending it
 * back to the AI agent — the data as JSON, plus a short plain-English "here's what I did"
 * line. Having one place for this keeps all of Cartograph's replies consistent and readable.
 */
package com.cartograph.mcp.tools;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolReply {

    private static final McpJsonMapper JSON = McpJsonDefaults.getMapper();

    private ToolReply() {
        // Helper holder; never instantiated.
    }

    /**
     * A successful reply. The {@code method} line is what the agent repeats to the user, so
     * it must read as ordinary English and carry the key number — never internal jargon.
     *
     * @param method a one-sentence, jargon-free account of what the tool checked
     * @param data   the result fields, which are merged alongside {@code method}
     */
    public static McpSchema.CallToolResult success(String method, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("method", method);
        payload.putAll(data);
        return McpSchema.CallToolResult.builder()
                .addTextContent(toJson(payload))
                .build();
    }

    /**
     * A failed reply. MCP expects tool-level problems to come back as a normal result marked
     * as an error, rather than as a transport exception, so the agent can read the message
     * and try something else.
     */
    public static McpSchema.CallToolResult failure(String message) {
        return McpSchema.CallToolResult.builder()
                .addTextContent(toJson(Map.of("error", message)))
                .isError(true)
                .build();
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (IOException e) {
            // Falling back to a hand-built string means a formatting fault still returns
            // something the agent can read, instead of killing the tool call.
            return "{\"error\":\"Could not format the result as JSON.\"}";
        }
    }

    /** Reads an optional whole-number argument, falling back to {@code fallback}. */
    public static int intArgument(Map<String, Object> arguments, String name, int fallback) {
        Object raw = arguments == null ? null : arguments.get(name);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    /** Reads a required text argument, returning null when it is missing or blank. */
    public static String stringArgument(Map<String, Object> arguments, String name) {
        Object raw = arguments == null ? null : arguments.get(name);
        if (raw instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return null;
    }
}
