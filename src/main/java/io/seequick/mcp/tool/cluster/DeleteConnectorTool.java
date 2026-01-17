package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.connector.KafkaConnector;
import io.strimzi.api.kafka.model.connector.KafkaConnectorList;
import io.seequick.mcp.tool.AbstractStrimziTool;

/**
 * Tool to delete a KafkaConnector resource.
 */
public class DeleteConnectorTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaConnector resource to delete"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the connector"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    public DeleteConnectorTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "delete_connector";
    }

    @Override
    protected String getDescription() {
        return "Delete a KafkaConnector resource (Kafka Connect will remove the connector)";
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

            // Get connector details before deletion
            String connectCluster = "";
            var labels = connector.getMetadata().getLabels();
            if (labels != null && labels.containsKey("strimzi.io/cluster")) {
                connectCluster = labels.get("strimzi.io/cluster");
            }

            String className = connector.getSpec() != null ? connector.getSpec().getClassName() : "unknown";

            // Delete the connector
            kubernetesClient.resources(KafkaConnector.class, KafkaConnectorList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .delete();

            StringBuilder result = new StringBuilder();
            result.append("Deleted KafkaConnector: ").append(namespace).append("/").append(name).append("\n");
            if (!connectCluster.isEmpty()) {
                result.append("  Connect Cluster: ").append(connectCluster).append("\n");
            }
            result.append("  Class: ").append(className).append("\n");
            result.append("\nThe connector and its tasks have been stopped.");

            return success(result.toString());
        } catch (Exception e) {
            return error("Error deleting connector: " + e.getMessage());
        }
    }
}
