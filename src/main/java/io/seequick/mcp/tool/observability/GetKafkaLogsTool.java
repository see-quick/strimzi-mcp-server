package io.seequick.mcp.tool.observability;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.List;

/**
 * Tool to fetch logs from Kafka broker pods.
 */
public class GetKafkaLogsTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the Kafka cluster"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the Kafka cluster"
                    },
                    "podName": {
                        "type": "string",
                        "description": "Optional: specific pod name. If not specified, gets logs from the first broker."
                    },
                    "lines": {
                        "type": "integer",
                        "description": "Number of log lines to retrieve (default: 100, max: 500)"
                    },
                    "container": {
                        "type": "string",
                        "description": "Container name (default: kafka)"
                    },
                    "previous": {
                        "type": "boolean",
                        "description": "Get logs from previous container instance (default: false)"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    public GetKafkaLogsTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "get_kafka_logs";
    }

    @Override
    protected String getDescription() {
        return "Fetch recent logs from Kafka broker pods";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    protected CallToolResult execute(McpSchema.CallToolRequest args) {
        try {
            String name = getStringArg(args, "name");
            String namespace = getStringArg(args, "namespace");
            String podName = getStringArg(args, "podName");
            int lines = getIntArg(args, "lines", 100);
            String container = getStringArg(args, "container");
            if (container == null) {
                container = "kafka";
            }
            Boolean previous = args.arguments().get("previous") != null ?
                    (Boolean) args.arguments().get("previous") : false;

            // Limit lines to prevent excessive output
            if (lines > 500) {
                lines = 500;
            }

            String targetPod = podName;

            if (targetPod == null) {
                // Find the first Kafka pod
                List<Pod> pods = kubernetesClient.pods()
                        .inNamespace(namespace)
                        .withLabel("strimzi.io/cluster", name)
                        .withLabel("strimzi.io/kind", "Kafka")
                        .list()
                        .getItems();

                if (pods.isEmpty()) {
                    return error("No Kafka pods found for cluster: " + namespace + "/" + name);
                }

                targetPod = pods.get(0).getMetadata().getName();
            }

            // Verify pod exists
            Pod pod = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withName(targetPod)
                    .get();

            if (pod == null) {
                return error("Pod not found: " + namespace + "/" + targetPod);
            }

            // Get logs
            String logs;
            if (Boolean.TRUE.equals(previous)) {
                logs = kubernetesClient.pods()
                        .inNamespace(namespace)
                        .withName(targetPod)
                        .inContainer(container)
                        .tailingLines(lines)
                        .withPrettyOutput()
                        .getLog(true);
            } else {
                logs = kubernetesClient.pods()
                        .inNamespace(namespace)
                        .withName(targetPod)
                        .inContainer(container)
                        .tailingLines(lines)
                        .getLog();
            }

            StringBuilder result = new StringBuilder();
            result.append("Logs from pod: ").append(namespace).append("/").append(targetPod).append("\n");
            result.append("Container: ").append(container).append("\n");
            result.append("Lines: ").append(lines);
            if (Boolean.TRUE.equals(previous)) {
                result.append(" (previous instance)");
            }
            result.append("\n");
            result.append("─".repeat(60)).append("\n\n");

            if (logs == null || logs.isEmpty()) {
                result.append("(no logs available)");
            } else {
                result.append(logs);
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error fetching logs: " + e.getMessage());
        }
    }
}
