package io.seequick.mcp.tool.topic;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.topic.KafkaTopic;
import io.strimzi.api.kafka.model.topic.KafkaTopicList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.List;
import java.util.Map;

/**
 * Tool to list KafkaTopics that are not in Ready state.
 */
public class GetUnreadyTopicsTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace to check. If not specified, checks all namespaces."
                    },
                    "kafkaCluster": {
                        "type": "string",
                        "description": "Filter by Kafka cluster name"
                    }
                }
            }
            """;

    public GetUnreadyTopicsTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "get_unready_topics";
    }

    @Override
    protected String getDescription() {
        return "List KafkaTopics that are not in Ready state (useful for troubleshooting)";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    protected CallToolResult execute(Map<String, Object> args) {
        try {
            String namespace = getStringArg(args, "namespace");
            String kafkaCluster = getStringArg(args, "kafkaCluster");

            KafkaTopicList topicList = listTopics(namespace, kafkaCluster);

            // Filter to unready topics
            List<KafkaTopic> unreadyTopics = topicList.getItems().stream()
                    .filter(this::isUnready)
                    .toList();

            StringBuilder result = new StringBuilder();
            if (unreadyTopics.isEmpty()) {
                result.append("All topics are in Ready state.");
            } else {
                result.append("Found ").append(unreadyTopics.size()).append(" unready topic(s):\n\n");

                for (KafkaTopic topic : unreadyTopics) {
                    result.append("- ").append(topic.getMetadata().getNamespace())
                            .append("/").append(topic.getMetadata().getName()).append("\n");

                    if (topic.getStatus() != null && topic.getStatus().getConditions() != null) {
                        for (var condition : topic.getStatus().getConditions()) {
                            result.append("    ").append(condition.getType())
                                    .append(": ").append(condition.getStatus());
                            if (condition.getReason() != null) {
                                result.append(" (").append(condition.getReason()).append(")");
                            }
                            result.append("\n");
                            if (condition.getMessage() != null) {
                                result.append("    Message: ").append(condition.getMessage()).append("\n");
                            }
                        }
                    } else {
                        result.append("    No status available\n");
                    }
                }
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error getting unready topics: " + e.getMessage());
        }
    }

    private boolean isUnready(KafkaTopic topic) {
        if (topic.getStatus() == null || topic.getStatus().getConditions() == null) {
            return true; // No status means not ready
        }
        return topic.getStatus().getConditions().stream()
                .filter(c -> "Ready".equals(c.getType()))
                .noneMatch(c -> "True".equals(c.getStatus()));
    }

    private KafkaTopicList listTopics(String namespace, String kafkaCluster) {
        if (namespace != null && !namespace.isEmpty()) {
            var resource = kubernetesClient.resources(KafkaTopic.class, KafkaTopicList.class)
                    .inNamespace(namespace);
            if (kafkaCluster != null && !kafkaCluster.isEmpty()) {
                return resource.withLabel("strimzi.io/cluster", kafkaCluster).list();
            }
            return resource.list();
        } else {
            var resource = kubernetesClient.resources(KafkaTopic.class, KafkaTopicList.class)
                    .inAnyNamespace();
            if (kafkaCluster != null && !kafkaCluster.isEmpty()) {
                return resource.withLabel("strimzi.io/cluster", kafkaCluster).list();
            }
            return resource.list();
        }
    }
}
