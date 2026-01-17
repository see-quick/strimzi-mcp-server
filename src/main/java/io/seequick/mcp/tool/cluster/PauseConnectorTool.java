package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.connector.KafkaConnector;
import io.strimzi.api.kafka.model.connector.KafkaConnectorList;
import io.seequick.mcp.tool.AbstractStrimziTool;

/**
 * Tool to pause a KafkaConnector.
 */
public class PauseConnectorTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaConnector resource to pause"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the connector"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    public PauseConnectorTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "pause_connector";
    }

    @Override
    protected String getDescription() {
        return "Pause a running KafkaConnector (stops processing without deleting)";
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

            KafkaConnector connector = kubernetesClient.resources(KafkaConnector.class, KafkaConnectorList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (connector == null) {
                return error("KafkaConnector not found: " + namespace + "/" + name);
            }

            // Check if already paused
            Boolean currentlyPaused = connector.getSpec() != null ? connector.getSpec().getPause() : false;
            if (Boolean.TRUE.equals(currentlyPaused)) {
                return success("KafkaConnector " + namespace + "/" + name + " is already paused.");
            }

            // Pause the connector
            kubernetesClient.resources(KafkaConnector.class, KafkaConnectorList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .edit(c -> {
                        c.getSpec().setPause(true);
                        return c;
                    });

            StringBuilder result = new StringBuilder();
            result.append("Paused KafkaConnector: ").append(namespace).append("/").append(name).append("\n");
            result.append("\nThe connector and its tasks will stop processing messages.\n");
            result.append("Use resume_connector to resume processing.");

            return success(result.toString());
        } catch (Exception e) {
            return error("Error pausing connector: " + e.getMessage());
        }
    }
}
