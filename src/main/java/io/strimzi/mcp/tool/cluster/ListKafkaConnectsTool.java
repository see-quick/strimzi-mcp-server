package io.strimzi.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.strimzi.api.kafka.model.connect.KafkaConnect;
import io.strimzi.api.kafka.model.connect.KafkaConnectList;
import io.strimzi.mcp.tool.AbstractStrimziTool;

import java.util.Map;

/**
 * Tool to list Strimzi KafkaConnect resources.
 */
public class ListKafkaConnectsTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace to list Kafka Connect clusters from. If not specified, lists from all namespaces."
                    }
                }
            }
            """;

    public ListKafkaConnectsTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "list_kafka_connects";
    }

    @Override
    protected String getDescription() {
        return "List Strimzi KafkaConnect clusters";
    }

    @Override
    protected String getInputSchema() {
        return SCHEMA;
    }

    @Override
    protected CallToolResult execute(Map<String, Object> args) {
        try {
            String namespace = getStringArg(args, "namespace");

            KafkaConnectList connectList;
            if (namespace != null && !namespace.isEmpty()) {
                connectList = kubernetesClient.resources(KafkaConnect.class, KafkaConnectList.class)
                        .inNamespace(namespace)
                        .list();
            } else {
                connectList = kubernetesClient.resources(KafkaConnect.class, KafkaConnectList.class)
                        .inAnyNamespace()
                        .list();
            }

            StringBuilder result = new StringBuilder();
            result.append("Found ").append(connectList.getItems().size()).append(" KafkaConnect cluster(s):\n\n");

            for (KafkaConnect connect : connectList.getItems()) {
                result.append("- ").append(connect.getMetadata().getNamespace())
                        .append("/").append(connect.getMetadata().getName());

                var spec = connect.getSpec();
                if (spec != null) {
                    result.append(" [replicas: ").append(spec.getReplicas()).append("]");
                    if (spec.getBootstrapServers() != null) {
                        result.append(" bootstrap: ").append(spec.getBootstrapServers());
                    }
                }

                // Status
                var status = connect.getStatus();
                if (status != null) {
                    if (status.getUrl() != null) {
                        result.append("\n    REST API: ").append(status.getUrl());
                    }
                    if (status.getConditions() != null) {
                        var readyCondition = status.getConditions().stream()
                                .filter(c -> "Ready".equals(c.getType()))
                                .findFirst();
                        readyCondition.ifPresent(c ->
                                result.append(" [Ready: ").append(c.getStatus()).append("]")
                        );
                    }
                    if (status.getConnectorPlugins() != null) {
                        result.append("\n    Plugins: ").append(status.getConnectorPlugins().size()).append(" installed");
                    }
                }

                result.append("\n");
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error listing Kafka Connect clusters: " + e.getMessage());
        }
    }
}
