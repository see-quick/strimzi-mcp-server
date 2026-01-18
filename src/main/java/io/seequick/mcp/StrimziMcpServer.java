package io.seequick.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.seequick.mcp.tool.StrimziTool;
import io.seequick.mcp.tool.factory.ClusterToolFactory;
import io.seequick.mcp.tool.factory.KafkaToolFactory;
import io.seequick.mcp.tool.factory.ObservabilityToolFactory;
import io.seequick.mcp.tool.factory.SecurityToolFactory;
import io.seequick.mcp.tool.factory.ToolFactory;
import io.seequick.mcp.tool.factory.TopicToolFactory;
import io.seequick.mcp.tool.factory.UserToolFactory;
import io.seequick.mcp.tool.factory.UtilityToolFactory;

import java.util.List;

/**
 * Strimzi MCP Server - provides MCP tools for interacting with Strimzi Kafka on Kubernetes.
 */
public class StrimziMcpServer {

    private static final String SERVER_NAME = "strimzi-mcp-server";
    private static final String SERVER_VERSION = "0.3.0";

    private static final List<ToolFactory> FACTORIES = List.of(
            new KafkaToolFactory(),
            new TopicToolFactory(),
            new UserToolFactory(),
            new ClusterToolFactory(),
            new ObservabilityToolFactory(),
            new SecurityToolFactory(),
            new UtilityToolFactory()
    );

    private final KubernetesClient kubernetesClient;
    private final List<StrimziTool> tools;

    public StrimziMcpServer(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
        this.tools = createTools();
    }

    public static void main(String[] args) {
        KubernetesClient client = new KubernetesClientBuilder().build();
        StrimziMcpServer server = new StrimziMcpServer(client);
        server.start();
    }

    /**
     * Creates all available Strimzi tools using factories.
     */
    private List<StrimziTool> createTools() {
        return FACTORIES.stream()
                .flatMap(factory -> factory.createTools(kubernetesClient).stream())
                .toList();
    }

    /**
     * Starts the MCP server with stdio transport.
     */
    public void start() {
        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(new JacksonMcpJsonMapper(new ObjectMapper()));

        McpSyncServer syncServer = McpServer.sync(transportProvider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .build();

        // Register all tools
        tools.forEach(tool -> syncServer.addTool(tool.getSpecification()));

        // Block main thread - the transport provider handles stdin/stdout
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            syncServer.close();
        }
    }
}
