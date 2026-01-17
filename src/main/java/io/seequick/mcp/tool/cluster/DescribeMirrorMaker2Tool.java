package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.mirrormaker2.KafkaMirrorMaker2;
import io.strimzi.api.kafka.model.mirrormaker2.KafkaMirrorMaker2List;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.Map;

/**
 * Tool to get detailed information about a KafkaMirrorMaker2.
 */
public class DescribeMirrorMaker2Tool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaMirrorMaker2 resource"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the MirrorMaker2"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    public DescribeMirrorMaker2Tool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "describe_mirrormaker2";
    }

    @Override
    protected String getDescription() {
        return "Get detailed information about a KafkaMirrorMaker2 including clusters, mirrors, and connectors";
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

            KafkaMirrorMaker2 mm2 = kubernetesClient.resources(KafkaMirrorMaker2.class, KafkaMirrorMaker2List.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (mm2 == null) {
                return error("KafkaMirrorMaker2 not found: " + namespace + "/" + name);
            }

            StringBuilder result = new StringBuilder();
            result.append("KafkaMirrorMaker2: ").append(namespace).append("/").append(name).append("\n\n");

            var spec = mm2.getSpec();
            if (spec != null) {
                result.append("Spec:\n");
                result.append("  Replicas: ").append(spec.getReplicas()).append("\n");
                if (spec.getVersion() != null) {
                    result.append("  Version: ").append(spec.getVersion()).append("\n");
                }
                if (spec.getConnectCluster() != null) {
                    result.append("  Connect Cluster: ").append(spec.getConnectCluster()).append("\n");
                }

                // Clusters
                if (spec.getClusters() != null && !spec.getClusters().isEmpty()) {
                    result.append("\nClusters:\n");
                    for (var cluster : spec.getClusters()) {
                        result.append("  - ").append(cluster.getAlias()).append("\n");
                        if (cluster.getBootstrapServers() != null) {
                            result.append("      Bootstrap: ").append(cluster.getBootstrapServers()).append("\n");
                        }
                        if (cluster.getAuthentication() != null) {
                            result.append("      Auth: ").append(cluster.getAuthentication().getType()).append("\n");
                        }
                        if (cluster.getTls() != null) {
                            result.append("      TLS: enabled\n");
                        }
                    }
                }

                // Mirrors
                if (spec.getMirrors() != null && !spec.getMirrors().isEmpty()) {
                    result.append("\nMirrors:\n");
                    for (var mirror : spec.getMirrors()) {
                        result.append("  - ").append(mirror.getSourceCluster())
                                .append(" -> ").append(mirror.getTargetCluster()).append("\n");
                        if (mirror.getSourceConnector() != null) {
                            result.append("      Source Connector: enabled\n");
                            if (mirror.getSourceConnector().getTasksMax() != null) {
                                result.append("        Tasks Max: ").append(mirror.getSourceConnector().getTasksMax()).append("\n");
                            }
                        }
                        if (mirror.getCheckpointConnector() != null) {
                            result.append("      Checkpoint Connector: enabled\n");
                        }
                        if (mirror.getHeartbeatConnector() != null) {
                            result.append("      Heartbeat Connector: enabled\n");
                        }
                        if (mirror.getTopicsPattern() != null) {
                            result.append("      Topics Pattern: ").append(mirror.getTopicsPattern()).append("\n");
                        }
                        if (mirror.getGroupsPattern() != null) {
                            result.append("      Groups Pattern: ").append(mirror.getGroupsPattern()).append("\n");
                        }
                    }
                }
            }

            // Status
            var status = mm2.getStatus();
            if (status != null) {
                result.append("\nStatus:\n");

                if (status.getUrl() != null) {
                    result.append("  REST API URL: ").append(status.getUrl()).append("\n");
                }
                result.append("  Replicas: ").append(status.getReplicas()).append("\n");

                if (status.getConnectors() != null) {
                    result.append("  Connectors: ").append(status.getConnectors().size()).append("\n");
                    for (var connector : status.getConnectors()) {
                        if (connector.containsKey("name")) {
                            result.append("    - ").append(connector.get("name"));
                        }
                        if (connector.containsKey("connector")) {
                            @SuppressWarnings("unchecked")
                            var connInfo = (Map<String, Object>) connector.get("connector");
                            if (connInfo != null && connInfo.containsKey("state")) {
                                result.append(" [").append(connInfo.get("state")).append("]");
                            }
                        }
                        result.append("\n");
                    }
                }

                if (status.getConditions() != null) {
                    result.append("  Conditions:\n");
                    for (var condition : status.getConditions()) {
                        result.append("    - ").append(condition.getType())
                                .append(": ").append(condition.getStatus());
                        if (condition.getReason() != null) {
                            result.append(" (").append(condition.getReason()).append(")");
                        }
                        result.append("\n");
                    }
                }

                result.append("  Observed Generation: ").append(status.getObservedGeneration()).append("\n");
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error describing MirrorMaker2: " + e.getMessage());
        }
    }
}
