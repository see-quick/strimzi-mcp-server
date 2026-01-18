package io.seequick.mcp.tool.factory;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.seequick.mcp.tool.StrimziTool;
import io.seequick.mcp.tool.observability.DescribeKafkaPodTool;
import io.seequick.mcp.tool.observability.GetKafkaEventsTool;
import io.seequick.mcp.tool.observability.GetKafkaLogsTool;
import io.seequick.mcp.tool.observability.GetOperatorLogsTool;
import io.seequick.mcp.tool.observability.HealthCheckTool;

import java.util.List;

/**
 * Factory for observability-related tools.
 */
public class ObservabilityToolFactory implements ToolFactory {

    @Override
    public List<StrimziTool> createTools(KubernetesClient client) {
        return List.of(
                new GetKafkaLogsTool(client),
                new GetOperatorLogsTool(client),
                new GetKafkaEventsTool(client),
                new DescribeKafkaPodTool(client),
                new HealthCheckTool(client)
        );
    }
}
