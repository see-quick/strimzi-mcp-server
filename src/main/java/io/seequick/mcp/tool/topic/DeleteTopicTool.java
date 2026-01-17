package io.seequick.mcp.tool.topic;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.topic.KafkaTopic;
import io.strimzi.api.kafka.model.topic.KafkaTopicList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.Map;

/**
 * Tool to delete a KafkaTopic resource.
 */
public class DeleteTopicTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaTopic resource to delete"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the topic"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    public DeleteTopicTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "delete_topic";
    }

    @Override
    protected String getDescription() {
        return "Delete a KafkaTopic resource (Topic Operator will delete the topic from Kafka)";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    protected CallToolResult execute(Map<String, Object> args) {
        try {
            String name = getStringArg(args, "name");
            String namespace = getStringArg(args, "namespace");

            KafkaTopic existing = kubernetesClient.resources(KafkaTopic.class, KafkaTopicList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (existing == null) {
                return error("KafkaTopic not found: " + namespace + "/" + name);
            }

            kubernetesClient.resources(KafkaTopic.class, KafkaTopicList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .delete();

            return success("Deleted KafkaTopic: " + namespace + "/" + name +
                    "\nThe Topic Operator will delete the topic from Kafka shortly.");
        } catch (Exception e) {
            return error("Error deleting topic: " + e.getMessage());
        }
    }
}
