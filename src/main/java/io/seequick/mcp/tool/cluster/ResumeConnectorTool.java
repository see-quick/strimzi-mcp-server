package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.connector.KafkaConnector;
import io.strimzi.api.kafka.model.connector.KafkaConnectorList;
import io.seequick.mcp.tool.AbstractStrimziTool;

/**
 * Tool to resume a paused KafkaConnector.
 */
public class ResumeConnectorTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaConnector resource to resume"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the connector"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    public ResumeConnectorTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "resume_connector";
    }

    @Override
    protected String getDescription() {
        return "Resume a paused KafkaConnector";
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

            // Check if already running (not paused)
            Boolean currentlyPaused = connector.getSpec() != null ? connector.getSpec().getPause() : false;
            if (!Boolean.TRUE.equals(currentlyPaused)) {
                return success("KafkaConnector " + namespace + "/" + name + " is already running (not paused).");
            }

            // Resume the connector
            kubernetesClient.resources(KafkaConnector.class, KafkaConnectorList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .edit(c -> {
                        c.getSpec().setPause(false);
                        return c;
                    });

            StringBuilder result = new StringBuilder();
            result.append("Resumed KafkaConnector: ").append(namespace).append("/").append(name).append("\n");
            result.append("\nThe connector and its tasks will resume processing messages.");

            return success(result.toString());
        } catch (Exception e) {
            return error("Error resuming connector: " + e.getMessage());
        }
    }
}
