package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.connector.KafkaConnector;
import io.strimzi.api.kafka.model.connector.KafkaConnectorList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.List;
import java.util.Map;

/**
 * Tool to get detailed information about a KafkaConnector.
 */
public class DescribeConnectorTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaConnector resource"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the connector"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    public DescribeConnectorTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "describe_connector";
    }

    @Override
    protected String getDescription() {
        return "Get detailed information about a KafkaConnector including configuration, tasks, and status";
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

            KafkaConnector connector = kubernetesClient.resources(KafkaConnector.class, KafkaConnectorList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (connector == null) {
                return error("KafkaConnector not found: " + namespace + "/" + name);
            }

            StringBuilder result = new StringBuilder();
            result.append("KafkaConnector: ").append(namespace).append("/").append(name).append("\n\n");

            // Cluster label
            var labels = connector.getMetadata().getLabels();
            if (labels != null && labels.containsKey("strimzi.io/cluster")) {
                result.append("Kafka Connect Cluster: ").append(labels.get("strimzi.io/cluster")).append("\n");
            }

            var spec = connector.getSpec();
            if (spec != null) {
                result.append("\nSpec:\n");
                result.append("  Class: ").append(spec.getClassName()).append("\n");
                if (spec.getTasksMax() != null) {
                    result.append("  Tasks Max: ").append(spec.getTasksMax()).append("\n");
                }
                if (spec.getPause() != null && spec.getPause()) {
                    result.append("  Paused: true\n");
                }
                if (spec.getAutoRestart() != null) {
                    result.append("  Auto Restart: enabled\n");
                    if (spec.getAutoRestart().getMaxRestarts() != null) {
                        result.append("    Max Restarts: ").append(spec.getAutoRestart().getMaxRestarts()).append("\n");
                    }
                }

                // Configuration
                if (spec.getConfig() != null && !spec.getConfig().isEmpty()) {
                    result.append("\nConfiguration:\n");
                    spec.getConfig().forEach((k, v) ->
                            result.append("  ").append(k).append(": ").append(v).append("\n")
                    );
                }
            }

            // Status
            var status = connector.getStatus();
            if (status != null) {
                result.append("\nStatus:\n");
                result.append("  Tasks Max: ").append(status.getTasksMax()).append("\n");

                // Connector status from Kafka Connect REST API
                if (status.getConnectorStatus() != null) {
                    var connectorStatus = status.getConnectorStatus();

                    if (connectorStatus.containsKey("connector")) {
                        @SuppressWarnings("unchecked")
                        var connInfo = (Map<String, Object>) connectorStatus.get("connector");
                        if (connInfo != null) {
                            result.append("\n  Connector:\n");
                            if (connInfo.containsKey("state")) {
                                result.append("    State: ").append(connInfo.get("state")).append("\n");
                            }
                            if (connInfo.containsKey("worker_id")) {
                                result.append("    Worker: ").append(connInfo.get("worker_id")).append("\n");
                            }
                        }
                    }

                    if (connectorStatus.containsKey("tasks")) {
                        @SuppressWarnings("unchecked")
                        var tasks = (List<Map<String, Object>>) connectorStatus.get("tasks");
                        if (tasks != null && !tasks.isEmpty()) {
                            result.append("\n  Tasks:\n");
                            for (var task : tasks) {
                                result.append("    - Task ").append(task.get("id"));
                                if (task.containsKey("state")) {
                                    result.append(": ").append(task.get("state"));
                                }
                                if (task.containsKey("worker_id")) {
                                    result.append(" on ").append(task.get("worker_id"));
                                }
                                if (task.containsKey("trace") && task.get("trace") != null) {
                                    String trace = task.get("trace").toString();
                                    // Show first line of stack trace
                                    String firstLine = trace.split("\n")[0];
                                    result.append("\n      Error: ").append(firstLine);
                                }
                                result.append("\n");
                            }
                        }
                    }
                }

                // Auto restart status
                if (status.getAutoRestart() != null) {
                    result.append("\n  Auto Restart Status:\n");
                    result.append("    Count: ").append(status.getAutoRestart().getCount()).append("\n");
                    if (status.getAutoRestart().getLastRestartTimestamp() != null) {
                        result.append("    Last Restart: ").append(status.getAutoRestart().getLastRestartTimestamp()).append("\n");
                    }
                }

                if (status.getConditions() != null) {
                    result.append("\n  Conditions:\n");
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
            return error("Error describing connector: " + e.getMessage());
        }
    }
}
