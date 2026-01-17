package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.strimzi.api.kafka.model.connector.KafkaConnector;
import io.strimzi.api.kafka.model.connector.KafkaConnectorList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.Map;

/**
 * Tool to list Strimzi KafkaConnector resources.
 */
public class ListConnectorsTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace to list connectors from. If not specified, lists from all namespaces."
                    },
                    "connectCluster": {
                        "type": "string",
                        "description": "Filter connectors by Kafka Connect cluster name (matches strimzi.io/cluster label)"
                    }
                }
            }
            """;

    public ListConnectorsTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "list_connectors";
    }

    @Override
    protected String getDescription() {
        return "List Strimzi KafkaConnector resources";
    }

    @Override
    protected String getInputSchema() {
        return SCHEMA;
    }

    @Override
    protected CallToolResult execute(Map<String, Object> args) {
        try {
            String namespace = getStringArg(args, "namespace");
            String connectCluster = getStringArg(args, "connectCluster");

            KafkaConnectorList connectorList = listConnectors(namespace, connectCluster);

            StringBuilder result = new StringBuilder();
            result.append("Found ").append(connectorList.getItems().size()).append(" KafkaConnector(s):\n\n");

            for (KafkaConnector connector : connectorList.getItems()) {
                result.append("- ").append(connector.getMetadata().getNamespace())
                        .append("/").append(connector.getMetadata().getName());

                var spec = connector.getSpec();
                if (spec != null) {
                    result.append(" [class: ").append(spec.getClassName()).append("]");
                    if (spec.getTasksMax() != null) {
                        result.append(" tasks: ").append(spec.getTasksMax());
                    }
                    if (spec.getPause() != null && spec.getPause()) {
                        result.append(" PAUSED");
                    }
                }

                // Status
                var status = connector.getStatus();
                if (status != null) {
                    if (status.getConnectorStatus() != null) {
                        var connectorStatus = status.getConnectorStatus();
                        if (connectorStatus.containsKey("connector")) {
                            @SuppressWarnings("unchecked")
                            var connectorInfo = (Map<String, Object>) connectorStatus.get("connector");
                            if (connectorInfo != null && connectorInfo.containsKey("state")) {
                                result.append(" state: ").append(connectorInfo.get("state"));
                            }
                        }
                    }
                    if (status.getTasksMax() > 0) {
                        result.append(" (").append(status.getTasksMax()).append(" tasks)");
                    }
                }

                // Cluster label
                var labels = connector.getMetadata().getLabels();
                if (labels != null && labels.containsKey("strimzi.io/cluster")) {
                    result.append(" -> ").append(labels.get("strimzi.io/cluster"));
                }

                result.append("\n");
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error listing connectors: " + e.getMessage());
        }
    }

    private KafkaConnectorList listConnectors(String namespace, String connectCluster) {
        if (namespace != null && !namespace.isEmpty()) {
            var resource = kubernetesClient.resources(KafkaConnector.class, KafkaConnectorList.class)
                    .inNamespace(namespace);
            if (connectCluster != null && !connectCluster.isEmpty()) {
                return resource.withLabel("strimzi.io/cluster", connectCluster).list();
            }
            return resource.list();
        } else {
            var resource = kubernetesClient.resources(KafkaConnector.class, KafkaConnectorList.class)
                    .inAnyNamespace();
            if (connectCluster != null && !connectCluster.isEmpty()) {
                return resource.withLabel("strimzi.io/cluster", connectCluster).list();
            }
            return resource.list();
        }
    }
}
