package io.seequick.mcp.tool.topic;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.strimzi.api.kafka.model.topic.KafkaTopic;
import io.strimzi.api.kafka.model.topic.KafkaTopicBuilder;
import io.strimzi.api.kafka.model.topic.KafkaTopicList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool to update configuration of an existing KafkaTopic.
 */
public class UpdateTopicConfigTool extends AbstractStrimziTool {

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
                    },
                    "partitions": {
                        "type": "integer",
                        "description": "New number of partitions (can only increase)"
                    },
                    "config": {
                        "type": "object",
                        "description": "Topic configuration to update as key-value pairs"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    public UpdateTopicConfigTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "update_topic_config";
    }

    @Override
    protected String getDescription() {
        return "Update configuration of an existing KafkaTopic (partitions can only be increased)";
    }

    @Override
    protected String getInputSchema() {
        return SCHEMA;
    }

    @Override
    protected CallToolResult execute(Map<String, Object> args) {
        try {
            String name = getStringArg(args, "name");
            String namespace = getStringArg(args, "namespace");
            Integer partitions = getOptionalIntArg(args, "partitions");
            Map<String, Object> config = getMapArg(args, "config");

            KafkaTopic existing = kubernetesClient.resources(KafkaTopic.class, KafkaTopicList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (existing == null) {
                return error("KafkaTopic not found: " + namespace + "/" + name);
            }

            if (partitions == null && (config == null || config.isEmpty())) {
                return error("No updates specified. Provide partitions or config to update.");
            }

            var topicBuilder = new KafkaTopicBuilder(existing);
            StringBuilder changes = new StringBuilder();

            if (partitions != null) {
                int currentPartitions = existing.getSpec().getPartitions();
                if (partitions < currentPartitions) {
                    return error("Cannot decrease partitions from " + currentPartitions +
                            " to " + partitions + ". Partitions can only be increased.");
                }
                topicBuilder.editSpec().withPartitions(partitions).endSpec();
                changes.append("  Partitions: ").append(currentPartitions).append(" -> ").append(partitions).append("\n");
            }

            if (config != null && !config.isEmpty()) {
                Map<String, Object> currentConfig = existing.getSpec().getConfig();
                Map<String, Object> newConfig = currentConfig != null ? new HashMap<>(currentConfig) : new HashMap<>();
                newConfig.putAll(config);
                topicBuilder.editSpec().withConfig(newConfig).endSpec();
                changes.append("  Config updates: ").append(config).append("\n");
            }

            KafkaTopic updatedTopic = topicBuilder.build();

            kubernetesClient.resources(KafkaTopic.class, KafkaTopicList.class)
                    .inNamespace(namespace)
                    .resource(updatedTopic)
                    .update();

            return success("Updated KafkaTopic: " + namespace + "/" + name + "\n" + changes +
                    "\nThe Topic Operator will apply the changes to Kafka shortly.");
        } catch (Exception e) {
            return error("Error updating topic: " + e.getMessage());
        }
    }
}
