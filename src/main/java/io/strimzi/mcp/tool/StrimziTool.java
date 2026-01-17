package io.strimzi.mcp.tool;

import io.modelcontextprotocol.server.McpServerFeatures;

/**
 * Interface for all Strimzi MCP tools.
 */
public interface StrimziTool {

    /**
     * Returns the MCP tool specification for this tool.
     */
    McpServerFeatures.SyncToolSpecification getSpecification();
}