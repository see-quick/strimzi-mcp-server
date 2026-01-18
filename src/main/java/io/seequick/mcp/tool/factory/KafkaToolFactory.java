package io.seequick.mcp.tool.factory;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.seequick.mcp.tool.StrimziTool;
import io.seequick.mcp.tool.kafka.GetKafkaListenersTool;
import io.seequick.mcp.tool.kafka.GetKafkaStatusTool;
import io.seequick.mcp.tool.kafka.ListKafkasTool;
import io.seequick.mcp.tool.kafka.RestartKafkaBrokerTool;
import io.seequick.mcp.tool.kafka.ScaleNodePoolTool;

import java.util.List;

/**
 * Factory for Kafka cluster-related tools.
 */
public class KafkaToolFactory implements ToolFactory {

    @Override
    public List<StrimziTool> createTools(KubernetesClient client) {
        return List.of(
                new ListKafkasTool(client),
                new GetKafkaStatusTool(client),
                new GetKafkaListenersTool(client),
                new RestartKafkaBrokerTool(client),
                new ScaleNodePoolTool(client)
        );
    }
}
