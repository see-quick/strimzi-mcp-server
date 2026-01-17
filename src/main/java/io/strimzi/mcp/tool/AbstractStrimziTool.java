package io.strimzi.mcp.tool;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.util.List;
import java.util.Map;

/**
 * Abstract base class for Strimzi MCP tools providing common functionality.
 */
public abstract class AbstractStrimziTool implements StrimziTool {

    protected final KubernetesClient kubernetesClient;

    protected AbstractStrimziTool(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    /**
     * Returns the name of the tool.
     */
    protected abstract String getName();

    /**
     * Returns the description of the tool.
     */
    protected abstract String getDescription();

    /**
     * Returns the JSON schema for the tool's input parameters.
     */
    protected abstract String getInputSchema();

    /**
     * Executes the tool with the given arguments.
     */
    protected abstract CallToolResult execute(Map<String, Object> args);

    @Override
    public McpServerFeatures.SyncToolSpecification getSpecification() {
        return new McpServerFeatures.SyncToolSpecification(
                new Tool(getName(), getDescription(), getInputSchema()),
                (exchange, args) -> execute(args)
        );
    }

    /**
     * Creates a successful result with the given text content.
     */
    protected CallToolResult success(String content) {
        return new CallToolResult(List.of(new TextContent(content)), false);
    }

    /**
     * Creates an error result with the given message.
     */
    protected CallToolResult error(String message) {
        return new CallToolResult(List.of(new TextContent(message)), true);
    }

    /**
     * Gets a string argument from the args map, returning null if not present.
     */
    protected String getStringArg(Map<String, Object> args, String key) {
        if (args == null) return null;
        Object value = args.get(key);
        return value != null ? (String) value : null;
    }

    /**
     * Gets an integer argument from the args map, returning the default if not present.
     */
    protected int getIntArg(Map<String, Object> args, String key, int defaultValue) {
        if (args == null) return defaultValue;
        Object value = args.get(key);
        return value != null ? ((Number) value).intValue() : defaultValue;
    }

    /**
     * Gets an optional integer argument from the args map, returning null if not present.
     */
    protected Integer getOptionalIntArg(Map<String, Object> args, String key) {
        if (args == null) return null;
        Object value = args.get(key);
        return value != null ? ((Number) value).intValue() : null;
    }

    /**
     * Gets a map argument from the args map, returning null if not present.
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getMapArg(Map<String, Object> args, String key) {
        if (args == null) return null;
        return (Map<String, Object>) args.get(key);
    }
}
