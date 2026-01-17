package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.strimzi.api.kafka.model.nodepool.KafkaNodePool;
import io.strimzi.api.kafka.model.nodepool.KafkaNodePoolList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.Map;

/**
 * Tool to get detailed information about a KafkaNodePool.
 */
public class DescribeNodePoolTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaNodePool resource"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the node pool"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    public DescribeNodePoolTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "describe_node_pool";
    }

    @Override
    protected String getDescription() {
        return "Get detailed information about a KafkaNodePool including spec, status, and node IDs";
    }

    @Override
    protected String getInputSchema() {
        return SCHEMA;
    }

    @Override
    protected CallToolResult execute(Map<String, Object> args) {
        try {
            String name = getStringArg(args, "name");
            String namespace = getStringArg(args, "namespace");

            KafkaNodePool pool = kubernetesClient.resources(KafkaNodePool.class, KafkaNodePoolList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (pool == null) {
                return error("KafkaNodePool not found: " + namespace + "/" + name);
            }

            StringBuilder result = new StringBuilder();
            result.append("KafkaNodePool: ").append(namespace).append("/").append(name).append("\n\n");

            // Cluster label
            var labels = pool.getMetadata().getLabels();
            if (labels != null && labels.containsKey("strimzi.io/cluster")) {
                result.append("Kafka Cluster: ").append(labels.get("strimzi.io/cluster")).append("\n");
            }

            var spec = pool.getSpec();
            if (spec != null) {
                result.append("\nSpec:\n");
                result.append("  Replicas: ").append(spec.getReplicas()).append("\n");

                if (spec.getRoles() != null) {
                    result.append("  Roles: ").append(spec.getRoles()).append("\n");
                }

                if (spec.getStorage() != null) {
                    result.append("  Storage Type: ").append(spec.getStorage().getType()).append("\n");
                }

                if (spec.getResources() != null) {
                    result.append("  Resources:\n");
                    if (spec.getResources().getRequests() != null) {
                        result.append("    Requests: ").append(spec.getResources().getRequests()).append("\n");
                    }
                    if (spec.getResources().getLimits() != null) {
                        result.append("    Limits: ").append(spec.getResources().getLimits()).append("\n");
                    }
                }

                if (spec.getJvmOptions() != null) {
                    result.append("  JVM Options:\n");
                    if (spec.getJvmOptions().getXms() != null) {
                        result.append("    -Xms: ").append(spec.getJvmOptions().getXms()).append("\n");
                    }
                    if (spec.getJvmOptions().getXmx() != null) {
                        result.append("    -Xmx: ").append(spec.getJvmOptions().getXmx()).append("\n");
                    }
                }
            }

            // Status
            var status = pool.getStatus();
            if (status != null) {
                result.append("\nStatus:\n");

                if (status.getNodeIds() != null) {
                    result.append("  Node IDs: ").append(status.getNodeIds()).append("\n");
                }
                if (status.getClusterId() != null) {
                    result.append("  Cluster ID: ").append(status.getClusterId()).append("\n");
                }
                if (status.getRoles() != null) {
                    result.append("  Roles: ").append(status.getRoles()).append("\n");
                }
                result.append("  Replicas: ").append(status.getReplicas()).append("\n");

                if (status.getConditions() != null) {
                    result.append("  Conditions:\n");
                    for (var condition : status.getConditions()) {
                        result.append("    - ").append(condition.getType())
                                .append(": ").append(condition.getStatus());
                        if (condition.getReason() != null) {
                            result.append(" (").append(condition.getReason()).append(")");
                        }
                        if (condition.getMessage() != null) {
                            result.append("\n      Message: ").append(condition.getMessage());
                        }
                        result.append("\n");
                    }
                }

                result.append("  Observed Generation: ").append(status.getObservedGeneration()).append("\n");
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error describing node pool: " + e.getMessage());
        }
    }
}
