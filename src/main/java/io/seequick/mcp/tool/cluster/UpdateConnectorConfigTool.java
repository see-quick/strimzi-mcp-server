package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.connector.KafkaConnector;
import io.strimzi.api.kafka.model.connector.KafkaConnectorList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool to update configuration of an existing KafkaConnector.
 */
public class UpdateConnectorConfigTool extends AbstractStrimziTool {

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
                    },
                    "config": {
                        "type": "object",
                        "description": "Configuration key-value pairs to update (merged with existing config)"
                    },
                    "tasksMax": {
                        "type": "integer",
                        "description": "Optional: update maximum number of tasks"
                    },
                    "replace": {
                        "type": "boolean",
                        "description": "If true, replace all config instead of merging (default: false)"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    public UpdateConnectorConfigTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "update_connector_config";
    }

    @Override
    protected String getDescription() {
        return "Update configuration of an existing KafkaConnector";
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
            Map<String, Object> newConfig = getMapArg(args, "config");
            Integer tasksMax = getOptionalIntArg(args, "tasksMax");
            Boolean replace = args.arguments().get("replace") != null ?
                    (Boolean) args.arguments().get("replace") : false;

            KafkaConnector connector = kubernetesClient.resources(KafkaConnector.class, KafkaConnectorList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (connector == null) {
                return error("KafkaConnector not found: " + namespace + "/" + name);
            }

            // Get existing config
            Map<String, Object> existingConfig = connector.getSpec().getConfig();
            int existingTasksMax = connector.getSpec().getTasksMax() != null ?
                    connector.getSpec().getTasksMax() : 1;

            // Prepare final config
            Map<String, Object> finalConfig;
            if (Boolean.TRUE.equals(replace) && newConfig != null) {
                finalConfig = new HashMap<>(newConfig);
            } else if (newConfig != null) {
                finalConfig = existingConfig != null ? new HashMap<>(existingConfig) : new HashMap<>();
                finalConfig.putAll(newConfig);
            } else {
                finalConfig = existingConfig;
            }

            int finalTasksMax = tasksMax != null ? tasksMax : existingTasksMax;

            // Update the connector
            kubernetesClient.resources(KafkaConnector.class, KafkaConnectorList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .edit(c -> {
                        if (finalConfig != null) {
                            c.getSpec().setConfig(finalConfig);
                        }
                        c.getSpec().setTasksMax(finalTasksMax);
                        return c;
                    });

            StringBuilder result = new StringBuilder();
            result.append("Updated KafkaConnector: ").append(namespace).append("/").append(name).append("\n\n");

            if (tasksMax != null && tasksMax != existingTasksMax) {
                result.append("Tasks Max: ").append(existingTasksMax).append(" -> ").append(tasksMax).append("\n");
            }

            if (newConfig != null) {
                result.append("Configuration updated (").append(replace ? "replaced" : "merged").append("):\n");
                for (String key : newConfig.keySet()) {
                    result.append("  ").append(key).append(": ").append(newConfig.get(key)).append("\n");
                }
            }

            result.append("\nThe connector will be reconfigured. Use describe_connector to check status.");

            return success(result.toString());
        } catch (Exception e) {
            return error("Error updating connector config: " + e.getMessage());
        }
    }
}
