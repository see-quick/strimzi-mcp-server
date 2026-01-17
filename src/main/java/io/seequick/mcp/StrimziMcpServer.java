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
// Cluster tools
import io.seequick.mcp.tool.cluster.ApproveRebalanceTool;
import io.seequick.mcp.tool.cluster.CreateConnectorTool;
import io.seequick.mcp.tool.cluster.CreateMirrorMaker2Tool;
import io.seequick.mcp.tool.cluster.CreateRebalanceTool;
import io.seequick.mcp.tool.cluster.DeleteConnectorTool;
import io.seequick.mcp.tool.cluster.DescribeBridgeTool;
import io.seequick.mcp.tool.cluster.DescribeConnectorTool;
import io.seequick.mcp.tool.cluster.DescribeKafkaConnectTool;
import io.seequick.mcp.tool.cluster.DescribeMirrorMaker2Tool;
import io.seequick.mcp.tool.cluster.DescribeNodePoolTool;
import io.seequick.mcp.tool.cluster.DescribeRebalanceTool;
import io.seequick.mcp.tool.cluster.GetClusterOperatorStatusTool;
import io.seequick.mcp.tool.cluster.ListBridgesTool;
import io.seequick.mcp.tool.cluster.ListConnectPluginsTool;
import io.seequick.mcp.tool.cluster.ListConnectorsTool;
import io.seequick.mcp.tool.cluster.ListKafkaConnectsTool;
import io.seequick.mcp.tool.cluster.ListMirrorMaker2sTool;
import io.seequick.mcp.tool.cluster.ListNodePoolsTool;
import io.seequick.mcp.tool.cluster.ListRebalancesTool;
import io.seequick.mcp.tool.cluster.PauseConnectorTool;
import io.seequick.mcp.tool.cluster.RefreshRebalanceTool;
import io.seequick.mcp.tool.cluster.RestartConnectorTool;
import io.seequick.mcp.tool.cluster.ResumeConnectorTool;
import io.seequick.mcp.tool.cluster.StopRebalanceTool;
import io.seequick.mcp.tool.cluster.UpdateConnectorConfigTool;
// Kafka tools
import io.seequick.mcp.tool.kafka.GetKafkaListenersTool;
import io.seequick.mcp.tool.kafka.GetKafkaStatusTool;
import io.seequick.mcp.tool.kafka.ListKafkasTool;
import io.seequick.mcp.tool.kafka.RestartKafkaBrokerTool;
import io.seequick.mcp.tool.kafka.ScaleNodePoolTool;
// Observability tools
import io.seequick.mcp.tool.observability.DescribeKafkaPodTool;
import io.seequick.mcp.tool.observability.GetKafkaEventsTool;
import io.seequick.mcp.tool.observability.GetKafkaLogsTool;
import io.seequick.mcp.tool.observability.GetOperatorLogsTool;
import io.seequick.mcp.tool.observability.HealthCheckTool;
// Security tools
import io.seequick.mcp.tool.security.GetCertificateExpiryTool;
import io.seequick.mcp.tool.security.ListCertificatesTool;
import io.seequick.mcp.tool.security.RotateUserCredentialsTool;
// Topic tools
import io.seequick.mcp.tool.topic.CompareTopicConfigTool;
import io.seequick.mcp.tool.topic.CreateTopicTool;
import io.seequick.mcp.tool.topic.DeleteTopicTool;
import io.seequick.mcp.tool.topic.DescribeTopicTool;
import io.seequick.mcp.tool.topic.GetTopicOperatorStatusTool;
import io.seequick.mcp.tool.topic.GetUnreadyTopicsTool;
import io.seequick.mcp.tool.topic.ListTopicsTool;
import io.seequick.mcp.tool.topic.UpdateTopicConfigTool;
// User tools
import io.seequick.mcp.tool.user.CreateUserTool;
import io.seequick.mcp.tool.user.DeleteUserTool;
import io.seequick.mcp.tool.user.DescribeUserTool;
import io.seequick.mcp.tool.user.GetUserCredentialsTool;
import io.seequick.mcp.tool.user.GetUserOperatorStatusTool;
import io.seequick.mcp.tool.user.ListUserAclsTool;
import io.seequick.mcp.tool.user.ListUsersTool;
import io.seequick.mcp.tool.user.UpdateUserAclsTool;
import io.seequick.mcp.tool.user.UpdateUserQuotasTool;
// Utility tools
import io.seequick.mcp.tool.utility.ExportResourceYamlTool;
import io.seequick.mcp.tool.utility.GetStrimziVersionTool;
import io.seequick.mcp.tool.utility.ListAllResourcesTool;

import java.util.List;

/**
 * Strimzi MCP Server - provides MCP tools for interacting with Strimzi Kafka on Kubernetes.
 */
public class StrimziMcpServer {

    private static final String SERVER_NAME = "strimzi-mcp-server";
    private static final String SERVER_VERSION = "0.3.0";

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
                new GetKafkaListenersTool(kubernetesClient),
                new RestartKafkaBrokerTool(kubernetesClient),
                new ScaleNodePoolTool(kubernetesClient),

                // Topic Operator tools
                new ListTopicsTool(kubernetesClient),
                new DescribeTopicTool(kubernetesClient),
                new CreateTopicTool(kubernetesClient),
                new DeleteTopicTool(kubernetesClient),
                new UpdateTopicConfigTool(kubernetesClient),
                new GetUnreadyTopicsTool(kubernetesClient),
                new GetTopicOperatorStatusTool(kubernetesClient),
                new CompareTopicConfigTool(kubernetesClient),

                // User Operator tools
                new ListUsersTool(kubernetesClient),
                new DescribeUserTool(kubernetesClient),
                new CreateUserTool(kubernetesClient),
                new DeleteUserTool(kubernetesClient),
                new GetUserCredentialsTool(kubernetesClient),
                new GetUserOperatorStatusTool(kubernetesClient),
                new UpdateUserAclsTool(kubernetesClient),
                new UpdateUserQuotasTool(kubernetesClient),
                new ListUserAclsTool(kubernetesClient),

                // Cluster Operator tools
                new ListNodePoolsTool(kubernetesClient),
                new DescribeNodePoolTool(kubernetesClient),
                new ListKafkaConnectsTool(kubernetesClient),
                new DescribeKafkaConnectTool(kubernetesClient),
                new ListConnectPluginsTool(kubernetesClient),
                new ListConnectorsTool(kubernetesClient),
                new DescribeConnectorTool(kubernetesClient),
                new CreateConnectorTool(kubernetesClient),
                new DeleteConnectorTool(kubernetesClient),
                new PauseConnectorTool(kubernetesClient),
                new ResumeConnectorTool(kubernetesClient),
                new RestartConnectorTool(kubernetesClient),
                new UpdateConnectorConfigTool(kubernetesClient),
                new ListRebalancesTool(kubernetesClient),
                new DescribeRebalanceTool(kubernetesClient),
                new CreateRebalanceTool(kubernetesClient),
                new ApproveRebalanceTool(kubernetesClient),
                new StopRebalanceTool(kubernetesClient),
                new RefreshRebalanceTool(kubernetesClient),
                new ListMirrorMaker2sTool(kubernetesClient),
                new DescribeMirrorMaker2Tool(kubernetesClient),
                new CreateMirrorMaker2Tool(kubernetesClient),
                new ListBridgesTool(kubernetesClient),
                new DescribeBridgeTool(kubernetesClient),
                new GetClusterOperatorStatusTool(kubernetesClient),

                // Observability tools
                new GetKafkaLogsTool(kubernetesClient),
                new GetOperatorLogsTool(kubernetesClient),
                new GetKafkaEventsTool(kubernetesClient),
                new DescribeKafkaPodTool(kubernetesClient),
                new HealthCheckTool(kubernetesClient),

                // Security tools
                new RotateUserCredentialsTool(kubernetesClient),
                new ListCertificatesTool(kubernetesClient),
                new GetCertificateExpiryTool(kubernetesClient),

                // Utility tools
                new ExportResourceYamlTool(kubernetesClient),
                new GetStrimziVersionTool(kubernetesClient),
                new ListAllResourcesTool(kubernetesClient)
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
