package io.seequick.mcp.tool.topic;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.topic.KafkaTopic;
import io.strimzi.api.kafka.model.topic.KafkaTopicList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.Map;

/**
 * Tool to list Strimzi KafkaTopic resources.
 */
public class ListTopicsTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace to list topics from. If not specified, lists from all namespaces."
                    },
                    "kafkaCluster": {
                        "type": "string",
                        "description": "Filter topics by Kafka cluster name (matches strimzi.io/cluster label)"
                    }
                }
            }
            """;

    public ListTopicsTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "list_topics";
    }

    @Override
    protected String getDescription() {
        return "List Strimzi KafkaTopic resources";
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

            StringBuilder result = new StringBuilder();
            result.append("Found ").append(topicList.getItems().size()).append(" KafkaTopic(s):\n\n");

            for (KafkaTopic topic : topicList.getItems()) {
                result.append("- ").append(topic.getMetadata().getNamespace())
                        .append("/").append(topic.getMetadata().getName());

                var spec = topic.getSpec();
                if (spec != null) {
                    result.append(" [partitions: ").append(spec.getPartitions())
                            .append(", replicas: ").append(spec.getReplicas()).append("]");
                }

                var labels = topic.getMetadata().getLabels();
                if (labels != null && labels.containsKey("strimzi.io/cluster")) {
                    result.append(" -> ").append(labels.get("strimzi.io/cluster"));
                }

                result.append("\n");
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error listing topics: " + e.getMessage());
        }
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
