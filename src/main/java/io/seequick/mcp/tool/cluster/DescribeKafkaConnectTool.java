package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.connect.KafkaConnect;
import io.strimzi.api.kafka.model.connect.KafkaConnectList;
import io.seequick.mcp.tool.AbstractStrimziTool;

/**
 * Tool to get detailed information about a KafkaConnect cluster.
 */
public class DescribeKafkaConnectTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaConnect resource"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the Kafka Connect cluster"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    public DescribeKafkaConnectTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "describe_kafka_connect";
    }

    @Override
    protected String getDescription() {
        return "Get detailed information about a KafkaConnect cluster including plugins and configuration";
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

            KafkaConnect connect = kubernetesClient.resources(KafkaConnect.class, KafkaConnectList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (connect == null) {
                return error("KafkaConnect not found: " + namespace + "/" + name);
            }

            StringBuilder result = new StringBuilder();
            result.append("KafkaConnect: ").append(namespace).append("/").append(name).append("\n\n");

            var spec = connect.getSpec();
            if (spec != null) {
                result.append("Spec:\n");
                result.append("  Replicas: ").append(spec.getReplicas()).append("\n");
                if (spec.getVersion() != null) {
                    result.append("  Version: ").append(spec.getVersion()).append("\n");
                }
                if (spec.getBootstrapServers() != null) {
                    result.append("  Bootstrap Servers: ").append(spec.getBootstrapServers()).append("\n");
                }
                if (spec.getImage() != null) {
                    result.append("  Image: ").append(spec.getImage()).append("\n");
                }

                // Build configuration
                if (spec.getBuild() != null) {
                    result.append("\nBuild Configuration:\n");
                    if (spec.getBuild().getOutput() != null) {
                        result.append("  Output Type: ").append(spec.getBuild().getOutput().getType()).append("\n");
                    }
                    if (spec.getBuild().getPlugins() != null) {
                        result.append("  Plugins to Install: ").append(spec.getBuild().getPlugins().size()).append("\n");
                        for (var plugin : spec.getBuild().getPlugins()) {
                            result.append("    - ").append(plugin.getName()).append("\n");
                        }
                    }
                }

                // Configuration
                if (spec.getConfig() != null && !spec.getConfig().isEmpty()) {
                    result.append("\nConfiguration:\n");
                    spec.getConfig().forEach((k, v) ->
                            result.append("  ").append(k).append(": ").append(v).append("\n")
                    );
                }

                // Authentication
                if (spec.getAuthentication() != null) {
                    result.append("\nAuthentication:\n");
                    result.append("  Type: ").append(spec.getAuthentication().getType()).append("\n");
                }

                // TLS
                if (spec.getTls() != null) {
                    result.append("\nTLS: enabled\n");
                }

                // Resources
                if (spec.getResources() != null) {
                    result.append("\nResources:\n");
                    if (spec.getResources().getRequests() != null) {
                        result.append("  Requests: ").append(spec.getResources().getRequests()).append("\n");
                    }
                    if (spec.getResources().getLimits() != null) {
                        result.append("  Limits: ").append(spec.getResources().getLimits()).append("\n");
                    }
                }

                // JVM Options
                if (spec.getJvmOptions() != null) {
                    result.append("\nJVM Options:\n");
                    if (spec.getJvmOptions().getXms() != null) {
                        result.append("  -Xms: ").append(spec.getJvmOptions().getXms()).append("\n");
                    }
                    if (spec.getJvmOptions().getXmx() != null) {
                        result.append("  -Xmx: ").append(spec.getJvmOptions().getXmx()).append("\n");
                    }
                }
            }

            // Status
            var status = connect.getStatus();
            if (status != null) {
                result.append("\nStatus:\n");

                if (status.getUrl() != null) {
                    result.append("  REST API URL: ").append(status.getUrl()).append("\n");
                }
                result.append("  Replicas: ").append(status.getReplicas()).append("\n");

                // Connector Plugins
                if (status.getConnectorPlugins() != null && !status.getConnectorPlugins().isEmpty()) {
                    result.append("\n  Available Plugins (").append(status.getConnectorPlugins().size()).append("):\n");
                    for (var plugin : status.getConnectorPlugins()) {
                        result.append("    - ").append(plugin.getConnectorClass());
                        if (plugin.getType() != null) {
                            result.append(" [").append(plugin.getType()).append("]");
                        }
                        if (plugin.getVersion() != null) {
                            result.append(" v").append(plugin.getVersion());
                        }
                        result.append("\n");
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
            return error("Error describing Kafka Connect: " + e.getMessage());
        }
    }
}
