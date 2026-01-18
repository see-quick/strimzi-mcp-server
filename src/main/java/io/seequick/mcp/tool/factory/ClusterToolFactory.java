package io.seequick.mcp.tool.factory;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.seequick.mcp.tool.StrimziTool;
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

import java.util.List;

/**
 * Factory for Cluster Operator-related tools (NodePools, Connect, Connectors, Rebalances, MirrorMaker2, Bridges).
 */
public class ClusterToolFactory implements ToolFactory {

    @Override
    public List<StrimziTool> createTools(KubernetesClient client) {
        return List.of(
                // Node Pools
                new ListNodePoolsTool(client),
                new DescribeNodePoolTool(client),

                // Kafka Connect
                new ListKafkaConnectsTool(client),
                new DescribeKafkaConnectTool(client),
                new ListConnectPluginsTool(client),

                // Connectors
                new ListConnectorsTool(client),
                new DescribeConnectorTool(client),
                new CreateConnectorTool(client),
                new DeleteConnectorTool(client),
                new PauseConnectorTool(client),
                new ResumeConnectorTool(client),
                new RestartConnectorTool(client),
                new UpdateConnectorConfigTool(client),

                // Rebalances (Cruise Control)
                new ListRebalancesTool(client),
                new DescribeRebalanceTool(client),
                new CreateRebalanceTool(client),
                new ApproveRebalanceTool(client),
                new StopRebalanceTool(client),
                new RefreshRebalanceTool(client),

                // MirrorMaker2
                new ListMirrorMaker2sTool(client),
                new DescribeMirrorMaker2Tool(client),
                new CreateMirrorMaker2Tool(client),

                // Bridges
                new ListBridgesTool(client),
                new DescribeBridgeTool(client),

                // Cluster Operator
                new GetClusterOperatorStatusTool(client)
        );
    }
}
