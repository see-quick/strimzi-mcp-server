package io.strimzi.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.strimzi.api.kafka.model.bridge.KafkaBridge;
import io.strimzi.api.kafka.model.bridge.KafkaBridgeList;
import io.strimzi.mcp.tool.AbstractStrimziTool;

import java.util.Map;

/**
 * Tool to list Strimzi KafkaBridge resources.
 */
public class ListBridgesTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace to list bridges from. If not specified, lists from all namespaces."
                    }
                }
            }
            """;

    public ListBridgesTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "list_bridges";
    }

    @Override
    protected String getDescription() {
        return "List Strimzi KafkaBridge resources for HTTP access to Kafka";
    }

    @Override
    protected String getInputSchema() {
        return SCHEMA;
    }

    @Override
    protected CallToolResult execute(Map<String, Object> args) {
        try {
            String namespace = getStringArg(args, "namespace");

            KafkaBridgeList bridgeList;
            if (namespace != null && !namespace.isEmpty()) {
                bridgeList = kubernetesClient.resources(KafkaBridge.class, KafkaBridgeList.class)
                        .inNamespace(namespace)
                        .list();
            } else {
                bridgeList = kubernetesClient.resources(KafkaBridge.class, KafkaBridgeList.class)
                        .inAnyNamespace()
                        .list();
            }

            StringBuilder result = new StringBuilder();
            result.append("Found ").append(bridgeList.getItems().size()).append(" KafkaBridge(s):\n\n");

            for (KafkaBridge bridge : bridgeList.getItems()) {
                result.append("- ").append(bridge.getMetadata().getNamespace())
                        .append("/").append(bridge.getMetadata().getName());

                var spec = bridge.getSpec();
                if (spec != null) {
                    result.append(" [replicas: ").append(spec.getReplicas()).append("]");
                    if (spec.getBootstrapServers() != null) {
                        result.append(" bootstrap: ").append(spec.getBootstrapServers());
                    }
                }

                // Status
                var status = bridge.getStatus();
                if (status != null) {
                    if (status.getUrl() != null) {
                        result.append("\n    HTTP URL: ").append(status.getUrl());
                    }
                    if (status.getConditions() != null) {
                        var readyCondition = status.getConditions().stream()
                                .filter(c -> "Ready".equals(c.getType()))
                                .findFirst();
                        readyCondition.ifPresent(c ->
                                result.append(" [Ready: ").append(c.getStatus()).append("]")
                        );
                    }
                }

                result.append("\n");
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error listing bridges: " + e.getMessage());
        }
    }
}
