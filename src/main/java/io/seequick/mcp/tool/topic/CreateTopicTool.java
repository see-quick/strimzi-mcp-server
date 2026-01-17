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
 * Tool to create a new KafkaTopic resource.
 */
public class CreateTopicTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaTopic resource to create"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace to create the topic in"
                    },
                    "kafkaCluster": {
                        "type": "string",
                        "description": "Name of the Kafka cluster (strimzi.io/cluster label)"
                    },
                    "partitions": {
                        "type": "integer",
                        "description": "Number of partitions (default: 1)"
                    },
                    "replicas": {
                        "type": "integer",
                        "description": "Number of replicas (default: 1)"
                    },
                    "config": {
                        "type": "object",
                        "description": "Topic configuration as key-value pairs (e.g., retention.ms, cleanup.policy)"
                    }
                },
                "required": ["name", "namespace", "kafkaCluster"]
            }
            """;

    public CreateTopicTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "create_topic";
    }

    @Override
    protected String getDescription() {
        return "Create a new KafkaTopic resource managed by the Topic Operator";
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
            String kafkaCluster = getStringArg(args, "kafkaCluster");
            int partitions = getIntArg(args, "partitions", 1);
            int replicas = getIntArg(args, "replicas", 1);
            Map<String, Object> config = getMapArg(args, "config");

            // Check if topic already exists
            KafkaTopic existing = kubernetesClient.resources(KafkaTopic.class, KafkaTopicList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (existing != null) {
                return error("KafkaTopic already exists: " + namespace + "/" + name);
            }

            // Build the topic
            var topicBuilder = new KafkaTopicBuilder()
                    .withNewMetadata()
                        .withName(name)
                        .withNamespace(namespace)
                        .addToLabels("strimzi.io/cluster", kafkaCluster)
                    .endMetadata()
                    .withNewSpec()
                        .withPartitions(partitions)
                        .withReplicas(replicas)
                    .endSpec();

            // Add config if provided
            if (config != null && !config.isEmpty()) {
                Map<String, Object> configMap = new HashMap<>(config);
                topicBuilder.editSpec().withConfig(configMap).endSpec();
            }

            KafkaTopic topic = topicBuilder.build();

            kubernetesClient.resources(KafkaTopic.class, KafkaTopicList.class)
                    .inNamespace(namespace)
                    .resource(topic)
                    .create();

            StringBuilder result = new StringBuilder();
            result.append("Created KafkaTopic: ").append(namespace).append("/").append(name).append("\n");
            result.append("  Kafka Cluster: ").append(kafkaCluster).append("\n");
            result.append("  Partitions: ").append(partitions).append("\n");
            result.append("  Replicas: ").append(replicas).append("\n");
            if (config != null && !config.isEmpty()) {
                result.append("  Config: ").append(config).append("\n");
            }
            result.append("\nThe Topic Operator will create the topic in Kafka shortly.");

            return success(result.toString());
        } catch (Exception e) {
            return error("Error creating topic: " + e.getMessage());
        }
    }
}
