package io.seequick.mcp.tool.topic;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.Map;

/**
 * Tool to get status of the Topic Operator (entity-operator pod) for a Kafka cluster.
 */
public class GetTopicOperatorStatusTool extends AbstractStrimziTool {

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

    public GetTopicOperatorStatusTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "get_topic_operator_status";
    }

    @Override
    protected String getDescription() {
        return "Get status of the Topic Operator (entity-operator pod) for a Kafka cluster";
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
            result.append("Topic Operator Status for cluster: ").append(namespace).append("/").append(kafkaCluster).append("\n\n");

            if (pods.getItems().isEmpty()) {
                result.append("No entity-operator pod found. The Topic Operator might not be deployed.\n");
                result.append("Check if entityOperator is configured in the Kafka resource.");
            } else {
                for (var pod : pods.getItems()) {
                    result.append("Pod: ").append(pod.getMetadata().getName()).append("\n");
                    result.append("  Phase: ").append(pod.getStatus().getPhase()).append("\n");

                    // Container statuses
                    if (pod.getStatus().getContainerStatuses() != null) {
                        result.append("  Containers:\n");
                        for (var containerStatus : pod.getStatus().getContainerStatuses()) {
                            result.append("    - ").append(containerStatus.getName())
                                    .append(": ready=").append(containerStatus.getReady())
                                    .append(", restarts=").append(containerStatus.getRestartCount());

                            if (containerStatus.getState() != null) {
                                if (containerStatus.getState().getRunning() != null) {
                                    result.append(" (running)");
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
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error getting Topic Operator status: " + e.getMessage());
        }
    }
}
