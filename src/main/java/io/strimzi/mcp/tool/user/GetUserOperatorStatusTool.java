package io.strimzi.mcp.tool.user;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.strimzi.mcp.tool.AbstractStrimziTool;

import java.util.Map;

/**
 * Tool to get status of the User Operator (entity-operator pod) for a Kafka cluster.
 */
public class GetUserOperatorStatusTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace where Kafka cluster is deployed"
                    },
                    "kafkaCluster": {
                        "type": "string",
                        "description": "Name of the Kafka cluster"
                    }
                },
                "required": ["namespace", "kafkaCluster"]
            }
            """;

    public GetUserOperatorStatusTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "get_user_operator_status";
    }

    @Override
    protected String getDescription() {
        return "Get status of the User Operator (entity-operator pod) for a Kafka cluster";
    }

    @Override
    protected String getInputSchema() {
        return SCHEMA;
    }

    @Override
    protected CallToolResult execute(Map<String, Object> args) {
        try {
            String namespace = getStringArg(args, "namespace");
            String kafkaCluster = getStringArg(args, "kafkaCluster");

            String entityOperatorPrefix = kafkaCluster + "-entity-operator";

            var pods = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withLabel("strimzi.io/cluster", kafkaCluster)
                    .withLabel("strimzi.io/kind", "Kafka")
                    .withLabel("strimzi.io/name", entityOperatorPrefix)
                    .list();

            StringBuilder result = new StringBuilder();
            result.append("User Operator Status for cluster: ").append(namespace).append("/").append(kafkaCluster).append("\n\n");

            if (pods.getItems().isEmpty()) {
                result.append("No entity-operator pod found. The User Operator might not be deployed.\n");
                result.append("Check if entityOperator is configured in the Kafka resource.");
            } else {
                for (var pod : pods.getItems()) {
                    result.append("Pod: ").append(pod.getMetadata().getName()).append("\n");
                    result.append("  Phase: ").append(pod.getStatus().getPhase()).append("\n");

                    // Find user-operator container
                    if (pod.getStatus().getContainerStatuses() != null) {
                        result.append("  User Operator Container:\n");
                        var userOpContainer = pod.getStatus().getContainerStatuses().stream()
                                .filter(c -> "user-operator".equals(c.getName()))
                                .findFirst();

                        if (userOpContainer.isPresent()) {
                            var containerStatus = userOpContainer.get();
                            result.append("    Ready: ").append(containerStatus.getReady()).append("\n");
                            result.append("    Restarts: ").append(containerStatus.getRestartCount()).append("\n");

                            if (containerStatus.getState() != null) {
                                if (containerStatus.getState().getRunning() != null) {
                                    result.append("    State: Running since ")
                                            .append(containerStatus.getState().getRunning().getStartedAt())
                                            .append("\n");
                                } else if (containerStatus.getState().getWaiting() != null) {
                                    result.append("    State: Waiting (")
                                            .append(containerStatus.getState().getWaiting().getReason())
                                            .append(")\n");
                                    if (containerStatus.getState().getWaiting().getMessage() != null) {
                                        result.append("    Message: ")
                                                .append(containerStatus.getState().getWaiting().getMessage())
                                                .append("\n");
                                    }
                                } else if (containerStatus.getState().getTerminated() != null) {
                                    result.append("    State: Terminated (")
                                            .append(containerStatus.getState().getTerminated().getReason())
                                            .append(")\n");
                                }
                            }
                        } else {
                            result.append("    user-operator container not found\n");
                        }
                    }
                }
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error getting User Operator status: " + e.getMessage());
        }
    }
}
