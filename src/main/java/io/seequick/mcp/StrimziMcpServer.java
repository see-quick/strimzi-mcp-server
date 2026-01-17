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
import io.seequick.mcp.tool.cluster.DescribeBridgeTool;
import io.seequick.mcp.tool.cluster.DescribeConnectorTool;
import io.seequick.mcp.tool.cluster.DescribeKafkaConnectTool;
import io.seequick.mcp.tool.cluster.DescribeMirrorMaker2Tool;
import io.seequick.mcp.tool.cluster.DescribeNodePoolTool;
import io.seequick.mcp.tool.cluster.DescribeRebalanceTool;
import io.seequick.mcp.tool.cluster.GetClusterOperatorStatusTool;
import io.seequick.mcp.tool.cluster.ListBridgesTool;
import io.seequick.mcp.tool.cluster.ListConnectorsTool;
import io.seequick.mcp.tool.cluster.ListKafkaConnectsTool;
import io.seequick.mcp.tool.cluster.ListMirrorMaker2sTool;
import io.seequick.mcp.tool.cluster.ListNodePoolsTool;
import io.seequick.mcp.tool.cluster.ListRebalancesTool;
import io.seequick.mcp.tool.kafka.GetKafkaStatusTool;
import io.seequick.mcp.tool.kafka.ListKafkasTool;
import io.seequick.mcp.tool.topic.CreateTopicTool;
import io.seequick.mcp.tool.topic.DeleteTopicTool;
import io.seequick.mcp.tool.topic.DescribeTopicTool;
import io.seequick.mcp.tool.topic.GetTopicOperatorStatusTool;
import io.seequick.mcp.tool.topic.GetUnreadyTopicsTool;
import io.seequick.mcp.tool.topic.ListTopicsTool;
import io.seequick.mcp.tool.topic.UpdateTopicConfigTool;
import io.seequick.mcp.tool.user.CreateUserTool;
import io.seequick.mcp.tool.user.DeleteUserTool;
import io.seequick.mcp.tool.user.DescribeUserTool;
import io.seequick.mcp.tool.user.GetUserCredentialsTool;
import io.seequick.mcp.tool.user.GetUserOperatorStatusTool;
import io.seequick.mcp.tool.user.ListUsersTool;

import java.util.List;

/**
 * Strimzi MCP Server - provides MCP tools for interacting with Strimzi Kafka on Kubernetes.
 */
public class StrimziMcpServer {

    private static final String SERVER_NAME = "strimzi-mcp-server";
    private static final String SERVER_VERSION = "0.1.0";

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
     * Creates all available Strimzi tools.
     */
    private List<StrimziTool> createTools() {
        return List.of(
                // Kafka cluster tools
                new ListKafkasTool(kubernetesClient),
                new GetKafkaStatusTool(kubernetesClient),

                // Topic Operator tools
                new ListTopicsTool(kubernetesClient),
                new DescribeTopicTool(kubernetesClient),
                new CreateTopicTool(kubernetesClient),
                new DeleteTopicTool(kubernetesClient),
                new UpdateTopicConfigTool(kubernetesClient),
                new GetUnreadyTopicsTool(kubernetesClient),
                new GetTopicOperatorStatusTool(kubernetesClient),

                // User Operator tools
                new ListUsersTool(kubernetesClient),
                new DescribeUserTool(kubernetesClient),
                new CreateUserTool(kubernetesClient),
                new DeleteUserTool(kubernetesClient),
                new GetUserCredentialsTool(kubernetesClient),
                new GetUserOperatorStatusTool(kubernetesClient),

                // Cluster Operator tools
                new ListNodePoolsTool(kubernetesClient),
                new DescribeNodePoolTool(kubernetesClient),
                new ListKafkaConnectsTool(kubernetesClient),
                new DescribeKafkaConnectTool(kubernetesClient),
                new ListConnectorsTool(kubernetesClient),
                new DescribeConnectorTool(kubernetesClient),
                new ListRebalancesTool(kubernetesClient),
                new DescribeRebalanceTool(kubernetesClient),
                new ListMirrorMaker2sTool(kubernetesClient),
                new DescribeMirrorMaker2Tool(kubernetesClient),
                new ListBridgesTool(kubernetesClient),
                new DescribeBridgeTool(kubernetesClient),
                new GetClusterOperatorStatusTool(kubernetesClient)
        );
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
