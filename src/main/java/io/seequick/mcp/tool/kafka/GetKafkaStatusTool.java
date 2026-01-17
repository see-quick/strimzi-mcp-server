package io.seequick.mcp.tool.kafka;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.kafka.Kafka;
import io.strimzi.api.kafka.model.kafka.KafkaList;
import io.seequick.mcp.tool.AbstractStrimziTool;

/**
 * Tool to get detailed status of a Strimzi Kafka cluster.
 */
public class GetKafkaStatusTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the Kafka cluster"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the Kafka cluster"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    public GetKafkaStatusTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "get_kafka_status";
    }

    @Override
    protected String getDescription() {
        return "Get detailed status of a Strimzi Kafka cluster";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    protected CallToolResult execute(McpSchema.CallToolRequest args) {
        try {
            String name = getStringArg(args, "name");
            String namespace = getStringArg(args, "namespace");

            Kafka kafka = kubernetesClient.resources(Kafka.class, KafkaList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (kafka == null) {
                return error("Kafka cluster not found: " + namespace + "/" + name);
            }

            StringBuilder result = new StringBuilder();
            result.append("Kafka Cluster: ").append(namespace).append("/").append(name).append("\n\n");

            // Spec summary
            var spec = kafka.getSpec();
            if (spec.getKafka() != null) {
                result.append("Kafka:\n");
                result.append("  Replicas: ").append(spec.getKafka().getReplicas()).append("\n");
                result.append("  Version: ").append(spec.getKafka().getVersion()).append("\n");
            }
            if (spec.getZookeeper() != null) {
                result.append("ZooKeeper:\n");
                result.append("  Replicas: ").append(spec.getZookeeper().getReplicas()).append("\n");
            }

            // Status
            var status = kafka.getStatus();
            if (status != null) {
                result.append("\nStatus:\n");

                if (status.getConditions() != null) {
                    result.append("  Conditions:\n");
                    for (var condition : status.getConditions()) {
                        result.append("    - ").append(condition.getType())
                                .append(": ").append(condition.getStatus());
                        if (condition.getMessage() != null) {
                            result.append(" (").append(condition.getMessage()).append(")");
                        }
                        result.append("\n");
                    }
                }

                if (status.getKafkaNodePools() != null && !status.getKafkaNodePools().isEmpty()) {
                    result.append("  Node Pools:\n");
                    for (var pool : status.getKafkaNodePools()) {
                        result.append("    - ").append(pool.getName()).append("\n");
                    }
                }

                if (status.getClusterId() != null) {
                    result.append("  Cluster ID: ").append(status.getClusterId()).append("\n");
                }
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error getting Kafka status: " + e.getMessage());
        }
    }
}
