package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.mirrormaker2.KafkaMirrorMaker2;
import io.strimzi.api.kafka.model.mirrormaker2.KafkaMirrorMaker2List;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.Map;

/**
 * Tool to list Strimzi KafkaMirrorMaker2 resources.
 */
public class ListMirrorMaker2sTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace to list MirrorMaker2 clusters from. If not specified, lists from all namespaces."
                    }
                }
            }
            """;

    public ListMirrorMaker2sTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "list_mirrormaker2s";
    }

    @Override
    protected String getDescription() {
        return "List Strimzi KafkaMirrorMaker2 resources for cross-cluster replication";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    protected CallToolResult execute(Map<String, Object> args) {
        try {
            String namespace = getStringArg(args, "namespace");

            KafkaMirrorMaker2List mm2List;
            if (namespace != null && !namespace.isEmpty()) {
                mm2List = kubernetesClient.resources(KafkaMirrorMaker2.class, KafkaMirrorMaker2List.class)
                        .inNamespace(namespace)
                        .list();
            } else {
                mm2List = kubernetesClient.resources(KafkaMirrorMaker2.class, KafkaMirrorMaker2List.class)
                        .inAnyNamespace()
                        .list();
            }

            StringBuilder result = new StringBuilder();
            result.append("Found ").append(mm2List.getItems().size()).append(" KafkaMirrorMaker2 cluster(s):\n\n");

            for (KafkaMirrorMaker2 mm2 : mm2List.getItems()) {
                result.append("- ").append(mm2.getMetadata().getNamespace())
                        .append("/").append(mm2.getMetadata().getName());

                var spec = mm2.getSpec();
                if (spec != null) {
                    result.append(" [replicas: ").append(spec.getReplicas()).append("]");
                    if (spec.getConnectCluster() != null) {
                        result.append(" connect: ").append(spec.getConnectCluster());
                    }
                }

                // Status
                var status = mm2.getStatus();
                if (status != null) {
                    if (status.getConditions() != null) {
                        var readyCondition = status.getConditions().stream()
                                .filter(c -> "Ready".equals(c.getType()))
                                .findFirst();
                        readyCondition.ifPresent(c ->
                                result.append(" [Ready: ").append(c.getStatus()).append("]")
                        );
                    }
                }

                // Show clusters being mirrored
                if (spec != null && spec.getClusters() != null) {
                    result.append("\n    Clusters: ");
                    spec.getClusters().forEach(cluster ->
                            result.append(cluster.getAlias()).append(" ")
                    );
                }

                if (spec != null && spec.getMirrors() != null) {
                    result.append("\n    Mirrors: ").append(spec.getMirrors().size()).append(" configured");
                }

                result.append("\n");
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error listing MirrorMaker2 clusters: " + e.getMessage());
        }
    }
}
