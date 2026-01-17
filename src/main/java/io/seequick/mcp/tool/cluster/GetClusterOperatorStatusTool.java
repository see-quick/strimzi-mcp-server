package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.Map;

/**
 * Tool to get status of the Strimzi Cluster Operator.
 */
public class GetClusterOperatorStatusTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace where Cluster Operator is deployed (default: strimzi)"
                    }
                }
            }
            """;

    public GetClusterOperatorStatusTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "get_cluster_operator_status";
    }

    @Override
    protected String getDescription() {
        return "Get status of the Strimzi Cluster Operator deployment";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    protected CallToolResult execute(Map<String, Object> args) {
        try {
            String namespace = getStringArg(args, "namespace");
            if (namespace == null || namespace.isEmpty()) {
                namespace = "strimzi"; // Default namespace
            }

            // Look for cluster operator pods
            var pods = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withLabel("strimzi.io/kind", "cluster-operator")
                    .list();

            StringBuilder result = new StringBuilder();
            result.append("Strimzi Cluster Operator Status\n");
            result.append("Namespace: ").append(namespace).append("\n\n");

            if (pods.getItems().isEmpty()) {
                // Try alternative label
                pods = kubernetesClient.pods()
                        .inNamespace(namespace)
                        .withLabel("name", "strimzi-cluster-operator")
                        .list();
            }

            if (pods.getItems().isEmpty()) {
                result.append("No Cluster Operator pods found in namespace '").append(namespace).append("'.\n");
                result.append("Try specifying the correct namespace where Strimzi is installed.");
            } else {
                result.append("Found ").append(pods.getItems().size()).append(" Cluster Operator pod(s):\n\n");

                for (var pod : pods.getItems()) {
                    result.append("Pod: ").append(pod.getMetadata().getName()).append("\n");
                    result.append("  Phase: ").append(pod.getStatus().getPhase()).append("\n");
                    result.append("  IP: ").append(pod.getStatus().getPodIP()).append("\n");
                    result.append("  Node: ").append(pod.getSpec().getNodeName()).append("\n");

                    // Container statuses
                    if (pod.getStatus().getContainerStatuses() != null) {
                        result.append("  Containers:\n");
                        for (var containerStatus : pod.getStatus().getContainerStatuses()) {
                            result.append("    - ").append(containerStatus.getName())
                                    .append(": ready=").append(containerStatus.getReady())
                                    .append(", restarts=").append(containerStatus.getRestartCount());

                            if (containerStatus.getState() != null) {
                                if (containerStatus.getState().getRunning() != null) {
                                    result.append(" (running since ")
                                            .append(containerStatus.getState().getRunning().getStartedAt())
                                            .append(")");
                                } else if (containerStatus.getState().getWaiting() != null) {
                                    result.append(" (waiting: ")
                                            .append(containerStatus.getState().getWaiting().getReason())
                                            .append(")");
                                } else if (containerStatus.getState().getTerminated() != null) {
                                    result.append(" (terminated: ")
                                            .append(containerStatus.getState().getTerminated().getReason())
                                            .append(")");
                                }
                            }
                            result.append("\n");

                            // Show image version
                            if (containerStatus.getImage() != null) {
                                result.append("      Image: ").append(containerStatus.getImage()).append("\n");
                            }
                        }
                    }

                    // Pod conditions
                    if (pod.getStatus().getConditions() != null) {
                        result.append("  Conditions:\n");
                        for (var condition : pod.getStatus().getConditions()) {
                            result.append("    - ").append(condition.getType())
                                    .append(": ").append(condition.getStatus());
                            if (condition.getMessage() != null && !condition.getMessage().isEmpty()) {
                                result.append(" (").append(condition.getMessage()).append(")");
                            }
                            result.append("\n");
                        }
                    }
                }

                // Also check the deployment
                var deployments = kubernetesClient.apps().deployments()
                        .inNamespace(namespace)
                        .withLabel("strimzi.io/kind", "cluster-operator")
                        .list();

                if (deployments.getItems().isEmpty()) {
                    deployments = kubernetesClient.apps().deployments()
                            .inNamespace(namespace)
                            .withLabel("name", "strimzi-cluster-operator")
                            .list();
                }

                if (!deployments.getItems().isEmpty()) {
                    result.append("\nDeployment Status:\n");
                    for (var deployment : deployments.getItems()) {
                        result.append("  ").append(deployment.getMetadata().getName()).append(": ");
                        var status = deployment.getStatus();
                        if (status != null) {
                            result.append("replicas=").append(status.getReplicas())
                                    .append(", ready=").append(status.getReadyReplicas())
                                    .append(", available=").append(status.getAvailableReplicas());
                        }
                        result.append("\n");
                    }
                }
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error getting Cluster Operator status: " + e.getMessage());
        }
    }
}
