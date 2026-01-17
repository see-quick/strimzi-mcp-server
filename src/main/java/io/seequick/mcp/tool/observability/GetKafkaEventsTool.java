package io.seequick.mcp.tool.observability;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.Comparator;
import java.util.List;

/**
 * Tool to get Kubernetes events for Strimzi resources.
 */
public class GetKafkaEventsTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace to get events from"
                    },
                    "kafkaCluster": {
                        "type": "string",
                        "description": "Optional: filter events by Kafka cluster name"
                    },
                    "resourceKind": {
                        "type": "string",
                        "enum": ["Kafka", "KafkaTopic", "KafkaUser", "KafkaConnect", "KafkaConnector", "Pod", "all"],
                        "description": "Filter by resource kind (default: all)"
                    },
                    "limit": {
                        "type": "integer",
                        "description": "Maximum number of events to return (default: 50)"
                    },
                    "warnings": {
                        "type": "boolean",
                        "description": "Only show Warning events (default: false)"
                    }
                },
                "required": ["namespace"]
            }
            """;

    public GetKafkaEventsTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "get_kafka_events";
    }

    @Override
    protected String getDescription() {
        return "Get Kubernetes events for Strimzi resources (useful for troubleshooting)";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    protected CallToolResult execute(McpSchema.CallToolRequest args) {
        try {
            String namespace = getStringArg(args, "namespace");
            String kafkaCluster = getStringArg(args, "kafkaCluster");
            String resourceKind = getStringArg(args, "resourceKind");
            int limit = getIntArg(args, "limit", 50);
            Boolean warningsOnly = args.arguments().get("warnings") != null ?
                    (Boolean) args.arguments().get("warnings") : false;

            // Get events from namespace
            List<Event> events = kubernetesClient.v1().events()
                    .inNamespace(namespace)
                    .list()
                    .getItems();

            // Filter events
            List<Event> filtered = events.stream()
                    .filter(e -> {
                        // Filter by type if warningsOnly
                        if (Boolean.TRUE.equals(warningsOnly) && !"Warning".equals(e.getType())) {
                            return false;
                        }

                        // Filter by resource kind
                        if (resourceKind != null && !"all".equalsIgnoreCase(resourceKind)) {
                            String involvedKind = e.getInvolvedObject() != null ?
                                    e.getInvolvedObject().getKind() : "";
                            if (!resourceKind.equalsIgnoreCase(involvedKind)) {
                                return false;
                            }
                        }

                        // Filter by Kafka cluster (check if resource name contains cluster name)
                        if (kafkaCluster != null) {
                            String involvedName = e.getInvolvedObject() != null ?
                                    e.getInvolvedObject().getName() : "";
                            if (!involvedName.contains(kafkaCluster)) {
                                return false;
                            }
                        }

                        return true;
                    })
                    // Sort by last timestamp (most recent first)
                    .sorted(Comparator.comparing(
                            e -> e.getLastTimestamp() != null ? e.getLastTimestamp() : "",
                            Comparator.reverseOrder()))
                    .limit(limit)
                    .toList();

            StringBuilder result = new StringBuilder();
            result.append("Kubernetes Events in namespace: ").append(namespace).append("\n");
            if (kafkaCluster != null) {
                result.append("Filtered by cluster: ").append(kafkaCluster).append("\n");
            }
            if (resourceKind != null && !"all".equalsIgnoreCase(resourceKind)) {
                result.append("Filtered by kind: ").append(resourceKind).append("\n");
            }
            if (Boolean.TRUE.equals(warningsOnly)) {
                result.append("Showing warnings only\n");
            }
            result.append("Found ").append(filtered.size()).append(" events\n");
            result.append("─".repeat(60)).append("\n\n");

            if (filtered.isEmpty()) {
                result.append("No events found matching the criteria.");
                return success(result.toString());
            }

            for (Event event : filtered) {
                // Event type (Normal/Warning)
                String type = event.getType() != null ? event.getType() : "Normal";
                String icon = "Warning".equals(type) ? "⚠" : "ℹ";

                result.append(icon).append(" ").append(type).append(" | ");

                // Timestamp
                if (event.getLastTimestamp() != null) {
                    result.append(event.getLastTimestamp()).append("\n");
                } else if (event.getEventTime() != null) {
                    result.append(event.getEventTime()).append("\n");
                } else {
                    result.append("(no timestamp)\n");
                }

                // Involved object
                if (event.getInvolvedObject() != null) {
                    result.append("   Resource: ").append(event.getInvolvedObject().getKind())
                            .append("/").append(event.getInvolvedObject().getName()).append("\n");
                }

                // Reason and message
                if (event.getReason() != null) {
                    result.append("   Reason: ").append(event.getReason()).append("\n");
                }
                if (event.getMessage() != null) {
                    result.append("   Message: ").append(event.getMessage()).append("\n");
                }

                // Count
                if (event.getCount() != null && event.getCount() > 1) {
                    result.append("   Count: ").append(event.getCount()).append("\n");
                }

                result.append("\n");
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error getting events: " + e.getMessage());
        }
    }
}
