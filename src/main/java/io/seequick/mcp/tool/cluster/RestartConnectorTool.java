package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.connector.KafkaConnector;
import io.strimzi.api.kafka.model.connector.KafkaConnectorList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.time.Instant;
import java.util.HashMap;

/**
 * Tool to restart a KafkaConnector or specific tasks.
 */
public class RestartConnectorTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaConnector resource to restart"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the connector"
                    },
                    "taskId": {
                        "type": "integer",
                        "description": "Optional: specific task ID to restart. If not specified, restarts the connector and all tasks."
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    private static final String RESTART_ANNOTATION = "strimzi.io/restart";
    private static final String RESTART_TASK_ANNOTATION = "strimzi.io/restart-task";

    public RestartConnectorTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "restart_connector";
    }

    @Override
    protected String getDescription() {
        return "Restart a KafkaConnector or a specific task via Strimzi annotation";
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
            Integer taskId = getOptionalIntArg(args, "taskId");

            KafkaConnector connector = kubernetesClient.resources(KafkaConnector.class, KafkaConnectorList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (connector == null) {
                return error("KafkaConnector not found: " + namespace + "/" + name);
            }

            String timestamp = Instant.now().toString();
            StringBuilder result = new StringBuilder();

            if (taskId != null) {
                // Restart specific task
                kubernetesClient.resources(KafkaConnector.class, KafkaConnectorList.class)
                        .inNamespace(namespace)
                        .withName(name)
                        .edit(c -> {
                            if (c.getMetadata().getAnnotations() == null) {
                                c.getMetadata().setAnnotations(new HashMap<>());
                            }
                            c.getMetadata().getAnnotations().put(RESTART_TASK_ANNOTATION, String.valueOf(taskId));
                            return c;
                        });

                result.append("Triggered restart for task ").append(taskId)
                        .append(" of KafkaConnector: ").append(namespace).append("/").append(name).append("\n");
            } else {
                // Restart entire connector
                kubernetesClient.resources(KafkaConnector.class, KafkaConnectorList.class)
                        .inNamespace(namespace)
                        .withName(name)
                        .edit(c -> {
                            if (c.getMetadata().getAnnotations() == null) {
                                c.getMetadata().setAnnotations(new HashMap<>());
                            }
                            c.getMetadata().getAnnotations().put(RESTART_ANNOTATION, timestamp);
                            return c;
                        });

                result.append("Triggered restart for KafkaConnector: ").append(namespace).append("/").append(name).append("\n");
                result.append("\nThe connector and all its tasks will be restarted.");
            }

            result.append("\nUse describe_connector to check the status.");

            return success(result.toString());
        } catch (Exception e) {
            return error("Error restarting connector: " + e.getMessage());
        }
    }
}
