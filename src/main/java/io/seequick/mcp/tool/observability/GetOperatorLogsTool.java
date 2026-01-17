package io.seequick.mcp.tool.observability;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.List;

/**
 * Tool to fetch logs from Strimzi operators (Cluster, Topic, User operators).
 */
public class GetOperatorLogsTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "operator": {
                        "type": "string",
                        "enum": ["cluster", "topic", "user"],
                        "description": "Which operator logs to fetch"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace (for topic/user operators, this is where Kafka is deployed)"
                    },
                    "kafkaCluster": {
                        "type": "string",
                        "description": "Required for topic/user operators: name of the Kafka cluster"
                    },
                    "lines": {
                        "type": "integer",
                        "description": "Number of log lines to retrieve (default: 100, max: 500)"
                    }
                },
                "required": ["operator", "namespace"]
            }
            """;

    public GetOperatorLogsTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "get_operator_logs";
    }

    @Override
    protected String getDescription() {
        return "Fetch logs from Strimzi operators (Cluster Operator, Topic Operator, User Operator)";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    protected CallToolResult execute(McpSchema.CallToolRequest args) {
        try {
            String operator = getStringArg(args, "operator");
            String namespace = getStringArg(args, "namespace");
            String kafkaCluster = getStringArg(args, "kafkaCluster");
            int lines = getIntArg(args, "lines", 100);

            if (lines > 500) {
                lines = 500;
            }

            String podName;
            String containerName;

            switch (operator.toLowerCase()) {
                case "cluster":
                    // Find Cluster Operator pod
                    List<Pod> coPods = kubernetesClient.pods()
                            .inNamespace(namespace)
                            .withLabel("name", "strimzi-cluster-operator")
                            .list()
                            .getItems();

                    if (coPods.isEmpty()) {
                        // Try alternative label
                        coPods = kubernetesClient.pods()
                                .inNamespace(namespace)
                                .withLabel("strimzi.io/kind", "cluster-operator")
                                .list()
                                .getItems();
                    }

                    if (coPods.isEmpty()) {
                        return error("Cluster Operator pod not found in namespace: " + namespace);
                    }

                    podName = coPods.get(0).getMetadata().getName();
                    containerName = "strimzi-cluster-operator";
                    break;

                case "topic":
                case "user":
                    if (kafkaCluster == null) {
                        return error("kafkaCluster is required for topic/user operator logs");
                    }

                    // Find Entity Operator pod
                    String eoPodName = kafkaCluster + "-entity-operator";
                    Pod eoPod = kubernetesClient.pods()
                            .inNamespace(namespace)
                            .withName(eoPodName)
                            .get();

                    if (eoPod == null) {
                        // Try finding by label
                        List<Pod> eoPods = kubernetesClient.pods()
                                .inNamespace(namespace)
                                .withLabel("strimzi.io/cluster", kafkaCluster)
                                .withLabel("strimzi.io/kind", "Kafka")
                                .withLabel("strimzi.io/name", kafkaCluster + "-entity-operator")
                                .list()
                                .getItems();

                        if (eoPods.isEmpty()) {
                            return error("Entity Operator pod not found for cluster: " + kafkaCluster);
                        }
                        podName = eoPods.get(0).getMetadata().getName();
                    } else {
                        podName = eoPodName;
                    }

                    containerName = "topic".equals(operator.toLowerCase()) ?
                            "topic-operator" : "user-operator";
                    break;

                default:
                    return error("Unknown operator type: " + operator + ". Use 'cluster', 'topic', or 'user'.");
            }

            // Get logs
            String logs = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .inContainer(containerName)
                    .tailingLines(lines)
                    .getLog();

            StringBuilder result = new StringBuilder();
            result.append("Logs from ").append(operator).append(" operator\n");
            result.append("Pod: ").append(namespace).append("/").append(podName).append("\n");
            result.append("Container: ").append(containerName).append("\n");
            result.append("Lines: ").append(lines).append("\n");
            result.append("─".repeat(60)).append("\n\n");

            if (logs == null || logs.isEmpty()) {
                result.append("(no logs available)");
            } else {
                result.append(logs);
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error fetching operator logs: " + e.getMessage());
        }
    }
}
