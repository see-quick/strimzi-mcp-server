package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.connect.KafkaConnect;
import io.strimzi.api.kafka.model.connect.KafkaConnectList;
import io.strimzi.api.kafka.model.connect.ConnectorPlugin;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.List;

/**
 * Tool to list available connector plugins in a Kafka Connect cluster.
 */
public class ListConnectPluginsTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaConnect cluster"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the Kafka Connect cluster"
                    },
                    "type": {
                        "type": "string",
                        "enum": ["source", "sink", "all"],
                        "description": "Filter by connector type (default: all)"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    public ListConnectPluginsTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "list_connect_plugins";
    }

    @Override
    protected String getDescription() {
        return "List available connector plugins in a Kafka Connect cluster";
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
            String typeFilter = getStringArg(args, "type");
            if (typeFilter == null) {
                typeFilter = "all";
            }

            KafkaConnect connect = kubernetesClient.resources(KafkaConnect.class, KafkaConnectList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (connect == null) {
                return error("KafkaConnect not found: " + namespace + "/" + name);
            }

            StringBuilder result = new StringBuilder();
            result.append("Kafka Connect Cluster: ").append(namespace).append("/").append(name).append("\n\n");

            var status = connect.getStatus();
            if (status == null || status.getConnectorPlugins() == null || status.getConnectorPlugins().isEmpty()) {
                result.append("No connector plugins found.\n");
                result.append("The Kafka Connect cluster may still be starting, or no plugins are installed.\n");
                result.append("\nTo add plugins, use the 'build' configuration in the KafkaConnect resource.");
                return success(result.toString());
            }

            List<ConnectorPlugin> plugins = status.getConnectorPlugins();
            String finalTypeFilter = typeFilter.toLowerCase();

            // Filter plugins by type
            List<ConnectorPlugin> filteredPlugins = plugins.stream()
                    .filter(p -> {
                        if ("all".equals(finalTypeFilter)) return true;
                        String pluginType = p.getType() != null ? p.getType().toLowerCase() : "";
                        return pluginType.contains(finalTypeFilter);
                    })
                    .toList();

            result.append("Available Plugins");
            if (!"all".equals(finalTypeFilter)) {
                result.append(" (").append(finalTypeFilter).append(" only)");
            }
            result.append(": ").append(filteredPlugins.size()).append("\n\n");

            // Group by type
            List<ConnectorPlugin> sourcePlugins = filteredPlugins.stream()
                    .filter(p -> p.getType() != null && p.getType().toLowerCase().contains("source"))
                    .toList();
            List<ConnectorPlugin> sinkPlugins = filteredPlugins.stream()
                    .filter(p -> p.getType() != null && p.getType().toLowerCase().contains("sink"))
                    .toList();
            List<ConnectorPlugin> otherPlugins = filteredPlugins.stream()
                    .filter(p -> p.getType() == null ||
                            (!p.getType().toLowerCase().contains("source") &&
                             !p.getType().toLowerCase().contains("sink")))
                    .toList();

            if (!sourcePlugins.isEmpty() && ("all".equals(finalTypeFilter) || "source".equals(finalTypeFilter))) {
                result.append("Source Connectors:\n");
                for (ConnectorPlugin plugin : sourcePlugins) {
                    result.append("  - ").append(plugin.getConnectorClass()).append("\n");
                    if (plugin.getVersion() != null) {
                        result.append("    Version: ").append(plugin.getVersion()).append("\n");
                    }
                }
                result.append("\n");
            }

            if (!sinkPlugins.isEmpty() && ("all".equals(finalTypeFilter) || "sink".equals(finalTypeFilter))) {
                result.append("Sink Connectors:\n");
                for (ConnectorPlugin plugin : sinkPlugins) {
                    result.append("  - ").append(plugin.getConnectorClass()).append("\n");
                    if (plugin.getVersion() != null) {
                        result.append("    Version: ").append(plugin.getVersion()).append("\n");
                    }
                }
                result.append("\n");
            }

            if (!otherPlugins.isEmpty()) {
                result.append("Other/Transforms:\n");
                for (ConnectorPlugin plugin : otherPlugins) {
                    result.append("  - ").append(plugin.getConnectorClass());
                    if (plugin.getType() != null) {
                        result.append(" (").append(plugin.getType()).append(")");
                    }
                    result.append("\n");
                }
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error listing connect plugins: " + e.getMessage());
        }
    }
}
