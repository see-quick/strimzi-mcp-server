package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.connector.KafkaConnector;
import io.strimzi.api.kafka.model.connector.KafkaConnectorBuilder;
import io.strimzi.api.kafka.model.connector.KafkaConnectorList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool to create a new KafkaConnector resource.
 */
public class CreateConnectorTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaConnector resource to create"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace to create the connector in"
                    },
                    "connectCluster": {
                        "type": "string",
                        "description": "Name of the Kafka Connect cluster (strimzi.io/cluster label)"
                    },
                    "className": {
                        "type": "string",
                        "description": "Fully qualified connector class name (e.g., org.apache.kafka.connect.file.FileStreamSourceConnector)"
                    },
                    "tasksMax": {
                        "type": "integer",
                        "description": "Maximum number of tasks (default: 1)"
                    },
                    "config": {
                        "type": "object",
                        "description": "Connector configuration as key-value pairs"
                    },
                    "autoRestart": {
                        "type": "boolean",
                        "description": "Enable automatic restart on failure (default: false)"
                    },
                    "pause": {
                        "type": "boolean",
                        "description": "Create connector in paused state (default: false)"
                    }
                },
                "required": ["name", "namespace", "connectCluster", "className"]
            }
            """;

    public CreateConnectorTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "create_connector";
    }

    @Override
    protected String getDescription() {
        return "Create a new KafkaConnector resource for Kafka Connect";
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
            String connectCluster = getStringArg(args, "connectCluster");
            String className = getStringArg(args, "className");
            int tasksMax = getIntArg(args, "tasksMax", 1);
            Map<String, Object> config = getMapArg(args, "config");
            Boolean autoRestart = args.arguments().get("autoRestart") != null ?
                    (Boolean) args.arguments().get("autoRestart") : false;
            Boolean pause = args.arguments().get("pause") != null ?
                    (Boolean) args.arguments().get("pause") : false;

            // Check if connector already exists
            KafkaConnector existing = kubernetesClient.resources(KafkaConnector.class, KafkaConnectorList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (existing != null) {
                return error("KafkaConnector already exists: " + namespace + "/" + name);
            }

            // Build the connector
            var connectorBuilder = new KafkaConnectorBuilder()
                    .withNewMetadata()
                        .withName(name)
                        .withNamespace(namespace)
                        .addToLabels("strimzi.io/cluster", connectCluster)
                    .endMetadata()
                    .withNewSpec()
                        .withClassName(className)
                        .withTasksMax(tasksMax)
                    .endSpec();

            // Add config if provided
            if (config != null && !config.isEmpty()) {
                Map<String, Object> configMap = new HashMap<>(config);
                connectorBuilder.editSpec().withConfig(configMap).endSpec();
            }

            // Set auto-restart if enabled
            if (autoRestart) {
                connectorBuilder.editSpec()
                        .withNewAutoRestart()
                            .withEnabled(true)
                        .endAutoRestart()
                        .endSpec();
            }

            // Set pause if requested
            if (pause) {
                connectorBuilder.editSpec().withPause(true).endSpec();
            }

            KafkaConnector connector = connectorBuilder.build();

            kubernetesClient.resources(KafkaConnector.class, KafkaConnectorList.class)
                    .inNamespace(namespace)
                    .resource(connector)
                    .create();

            StringBuilder result = new StringBuilder();
            result.append("Created KafkaConnector: ").append(namespace).append("/").append(name).append("\n");
            result.append("  Connect Cluster: ").append(connectCluster).append("\n");
            result.append("  Class: ").append(className).append("\n");
            result.append("  Tasks Max: ").append(tasksMax).append("\n");
            if (autoRestart) {
                result.append("  Auto Restart: enabled\n");
            }
            if (pause) {
                result.append("  Initial State: paused\n");
            }
            if (config != null && !config.isEmpty()) {
                result.append("  Config entries: ").append(config.size()).append("\n");
            }
            result.append("\nThe Kafka Connect cluster will create the connector shortly.");

            return success(result.toString());
        } catch (Exception e) {
            return error("Error creating connector: " + e.getMessage());
        }
    }
}
