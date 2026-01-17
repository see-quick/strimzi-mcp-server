package io.seequick.mcp.tool.topic;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.topic.KafkaTopic;
import io.strimzi.api.kafka.model.topic.KafkaTopicList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tool to compare configuration between two topics or against defaults.
 */
public class CompareTopicConfigTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "topic1": {
                        "type": "string",
                        "description": "Name of the first KafkaTopic"
                    },
                    "topic2": {
                        "type": "string",
                        "description": "Name of the second KafkaTopic (optional, compare against first topic's current vs desired if omitted)"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the topics"
                    }
                },
                "required": ["topic1", "namespace"]
            }
            """;

    // Common Kafka topic default values
    private static final Map<String, String> DEFAULT_TOPIC_CONFIG = Map.of(
            "cleanup.policy", "delete",
            "compression.type", "producer",
            "retention.ms", "604800000",
            "segment.bytes", "1073741824",
            "min.insync.replicas", "1",
            "max.message.bytes", "1048588"
    );

    public CompareTopicConfigTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "compare_topic_config";
    }

    @Override
    protected String getDescription() {
        return "Compare configuration between two topics or show how a topic differs from Kafka defaults";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    protected CallToolResult execute(McpSchema.CallToolRequest args) {
        try {
            String topic1Name = getStringArg(args, "topic1");
            String topic2Name = getStringArg(args, "topic2");
            String namespace = getStringArg(args, "namespace");

            KafkaTopic topic1 = kubernetesClient.resources(KafkaTopic.class, KafkaTopicList.class)
                    .inNamespace(namespace)
                    .withName(topic1Name)
                    .get();

            if (topic1 == null) {
                return error("KafkaTopic not found: " + namespace + "/" + topic1Name);
            }

            StringBuilder result = new StringBuilder();

            if (topic2Name != null) {
                // Compare two topics
                KafkaTopic topic2 = kubernetesClient.resources(KafkaTopic.class, KafkaTopicList.class)
                        .inNamespace(namespace)
                        .withName(topic2Name)
                        .get();

                if (topic2 == null) {
                    return error("KafkaTopic not found: " + namespace + "/" + topic2Name);
                }

                result.append("Comparing Topics\n");
                result.append("═".repeat(60)).append("\n");
                result.append("Topic 1: ").append(namespace).append("/").append(topic1Name).append("\n");
                result.append("Topic 2: ").append(namespace).append("/").append(topic2Name).append("\n\n");

                // Compare spec
                var spec1 = topic1.getSpec();
                var spec2 = topic2.getSpec();

                result.append("BASIC PROPERTIES\n");
                result.append("─".repeat(40)).append("\n");
                result.append(String.format("  %-20s %-15s %-15s%n", "Property", topic1Name, topic2Name));
                result.append("  ").append("-".repeat(50)).append("\n");

                int part1 = spec1.getPartitions() != null ? spec1.getPartitions() : 1;
                int part2 = spec2.getPartitions() != null ? spec2.getPartitions() : 1;
                String partDiff = part1 != part2 ? " ≠" : "";
                result.append(String.format("  %-20s %-15d %-15d%s%n", "partitions", part1, part2, partDiff));

                int rep1 = spec1.getReplicas() != null ? spec1.getReplicas() : 1;
                int rep2 = spec2.getReplicas() != null ? spec2.getReplicas() : 1;
                String repDiff = rep1 != rep2 ? " ≠" : "";
                result.append(String.format("  %-20s %-15d %-15d%s%n", "replicas", rep1, rep2, repDiff));
                result.append("\n");

                // Compare config
                Map<String, Object> config1 = spec1.getConfig() != null ? spec1.getConfig() : new HashMap<>();
                Map<String, Object> config2 = spec2.getConfig() != null ? spec2.getConfig() : new HashMap<>();

                Set<String> allKeys = new HashSet<>();
                allKeys.addAll(config1.keySet());
                allKeys.addAll(config2.keySet());

                if (!allKeys.isEmpty()) {
                    result.append("CONFIGURATION\n");
                    result.append("─".repeat(40)).append("\n");
                    result.append(String.format("  %-30s %-20s %-20s%n", "Config Key", topic1Name, topic2Name));
                    result.append("  ").append("-".repeat(70)).append("\n");

                    for (String key : allKeys.stream().sorted().toList()) {
                        String val1 = config1.containsKey(key) ? String.valueOf(config1.get(key)) : "(default)";
                        String val2 = config2.containsKey(key) ? String.valueOf(config2.get(key)) : "(default)";
                        String diff = !val1.equals(val2) ? " ≠" : "";
                        result.append(String.format("  %-30s %-20s %-20s%s%n", key, truncate(val1, 18), truncate(val2, 18), diff));
                    }
                } else {
                    result.append("Both topics use default configuration.\n");
                }

            } else {
                // Compare topic against defaults
                result.append("Topic Configuration vs Kafka Defaults\n");
                result.append("═".repeat(60)).append("\n");
                result.append("Topic: ").append(namespace).append("/").append(topic1Name).append("\n\n");

                var spec = topic1.getSpec();
                Map<String, Object> config = spec.getConfig() != null ? spec.getConfig() : new HashMap<>();

                result.append("BASIC PROPERTIES\n");
                result.append("─".repeat(40)).append("\n");
                result.append("  Partitions: ").append(spec.getPartitions() != null ? spec.getPartitions() : 1).append("\n");
                result.append("  Replicas: ").append(spec.getReplicas() != null ? spec.getReplicas() : 1).append("\n\n");

                if (config.isEmpty()) {
                    result.append("This topic uses all default Kafka configuration values.\n");
                } else {
                    result.append("CUSTOM CONFIGURATION (differs from defaults)\n");
                    result.append("─".repeat(40)).append("\n");
                    result.append(String.format("  %-30s %-20s %-20s%n", "Config Key", "Current", "Default"));
                    result.append("  ").append("-".repeat(70)).append("\n");

                    for (Map.Entry<String, Object> entry : config.entrySet()) {
                        String key = entry.getKey();
                        String currentVal = String.valueOf(entry.getValue());
                        String defaultVal = DEFAULT_TOPIC_CONFIG.getOrDefault(key, "(Kafka default)");
                        result.append(String.format("  %-30s %-20s %-20s%n", key, truncate(currentVal, 18), truncate(defaultVal, 18)));
                    }
                }
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error comparing topics: " + e.getMessage());
        }
    }

    private String truncate(String value, int maxLen) {
        if (value.length() <= maxLen) return value;
        return value.substring(0, maxLen - 3) + "...";
    }
}
