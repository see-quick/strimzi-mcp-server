package io.seequick.mcp.tool.topic;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.topic.KafkaTopic;
import io.strimzi.api.kafka.model.topic.KafkaTopicList;
import io.seequick.mcp.tool.AbstractStrimziTool;

/**
 * Tool to get detailed information about a KafkaTopic.
 */
public class DescribeTopicTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaTopic resource"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the topic"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    public DescribeTopicTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "describe_topic";
    }

    @Override
    protected String getDescription() {
        return "Get detailed information about a KafkaTopic including spec, status, and configuration";
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

            KafkaTopic topic = kubernetesClient.resources(KafkaTopic.class, KafkaTopicList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (topic == null) {
                return error("KafkaTopic not found: " + namespace + "/" + name);
            }

            StringBuilder result = new StringBuilder();
            result.append("KafkaTopic: ").append(namespace).append("/").append(name).append("\n\n");

            // Metadata
            var labels = topic.getMetadata().getLabels();
            if (labels != null && labels.containsKey("strimzi.io/cluster")) {
                result.append("Kafka Cluster: ").append(labels.get("strimzi.io/cluster")).append("\n");
            }

            // Spec
            var spec = topic.getSpec();
            if (spec != null) {
                result.append("\nSpec:\n");
                result.append("  Partitions: ").append(spec.getPartitions()).append("\n");
                result.append("  Replicas: ").append(spec.getReplicas()).append("\n");

                if (spec.getConfig() != null && !spec.getConfig().isEmpty()) {
                    result.append("  Config:\n");
                    spec.getConfig().forEach((key, value) ->
                            result.append("    ").append(key).append(": ").append(value).append("\n")
                    );
                }
            }

            // Status
            var status = topic.getStatus();
            if (status != null) {
                result.append("\nStatus:\n");

                if (status.getTopicName() != null) {
                    result.append("  Topic Name in Kafka: ").append(status.getTopicName()).append("\n");
                }

                if (status.getConditions() != null) {
                    result.append("  Conditions:\n");
                    for (var condition : status.getConditions()) {
                        result.append("    - ").append(condition.getType())
                                .append(": ").append(condition.getStatus());
                        if (condition.getReason() != null) {
                            result.append(" (").append(condition.getReason()).append(")");
                        }
                        if (condition.getMessage() != null) {
                            result.append("\n      Message: ").append(condition.getMessage());
                        }
                        result.append("\n");
                    }
                }

                result.append("  Observed Generation: ").append(status.getObservedGeneration()).append("\n");
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error describing topic: " + e.getMessage());
        }
    }
}
