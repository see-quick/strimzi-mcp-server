package io.seequick.mcp.tool.topic;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.topic.KafkaTopic;
import io.strimzi.api.kafka.model.topic.KafkaTopicList;
import io.seequick.mcp.tool.AbstractStrimziTool;

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
    protected CallToolResult execute(McpSchema.CallToolRequest args) {
        try {
            String name = getStringArg(args, "name");
            String namespace = getStringArg(args, "namespace");

            // Ensure topic exists
            ensureExists(KafkaTopic.class, KafkaTopicList.class, namespace, name, "KafkaTopic");

            // Delete the topic
            deleteResource(KafkaTopic.class, KafkaTopicList.class, namespace, name);

            return success("Deleted KafkaTopic: " + namespace + "/" + name +
                    "\nThe Topic Operator will delete the topic from Kafka shortly.");
        } catch (ResourceNotFoundException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            return error("Error deleting topic: " + e.getMessage());
        }
    }
}
