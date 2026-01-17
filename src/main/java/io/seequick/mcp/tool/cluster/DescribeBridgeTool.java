package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.bridge.KafkaBridge;
import io.strimzi.api.kafka.model.bridge.KafkaBridgeList;
import io.seequick.mcp.tool.AbstractStrimziTool;

/**
 * Tool to get detailed information about a KafkaBridge.
 */
public class DescribeBridgeTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaBridge resource"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the bridge"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    public DescribeBridgeTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "describe_bridge";
    }

    @Override
    protected String getDescription() {
        return "Get detailed information about a KafkaBridge including HTTP configuration and status";
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

            KafkaBridge bridge = kubernetesClient.resources(KafkaBridge.class, KafkaBridgeList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (bridge == null) {
                return error("KafkaBridge not found: " + namespace + "/" + name);
            }

            StringBuilder result = new StringBuilder();
            result.append("KafkaBridge: ").append(namespace).append("/").append(name).append("\n\n");

            var spec = bridge.getSpec();
            if (spec != null) {
                result.append("Spec:\n");
                result.append("  Replicas: ").append(spec.getReplicas()).append("\n");
                if (spec.getBootstrapServers() != null) {
                    result.append("  Bootstrap Servers: ").append(spec.getBootstrapServers()).append("\n");
                }

                // HTTP configuration
                if (spec.getHttp() != null) {
                    result.append("\nHTTP Configuration:\n");
                    if (spec.getHttp().getPort() != 0) {
                        result.append("  Port: ").append(spec.getHttp().getPort()).append("\n");
                    }
                    if (spec.getHttp().getCors() != null) {
                        result.append("  CORS: enabled\n");
                        if (spec.getHttp().getCors().getAllowedOrigins() != null) {
                            result.append("    Allowed Origins: ").append(spec.getHttp().getCors().getAllowedOrigins()).append("\n");
                        }
                        if (spec.getHttp().getCors().getAllowedMethods() != null) {
                            result.append("    Allowed Methods: ").append(spec.getHttp().getCors().getAllowedMethods()).append("\n");
                        }
                    }
                }

                // Producer configuration
                if (spec.getProducer() != null) {
                    result.append("\nProducer Configuration:\n");
                    if (spec.getProducer().getConfig() != null) {
                        spec.getProducer().getConfig().forEach((k, v) ->
                                result.append("  ").append(k).append(": ").append(v).append("\n")
                        );
                    }
                }

                // Consumer configuration
                if (spec.getConsumer() != null) {
                    result.append("\nConsumer Configuration:\n");
                    if (spec.getConsumer().getConfig() != null) {
                        spec.getConsumer().getConfig().forEach((k, v) ->
                                result.append("  ").append(k).append(": ").append(v).append("\n")
                        );
                    }
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
            }

            // Status
            var status = bridge.getStatus();
            if (status != null) {
                result.append("\nStatus:\n");

                if (status.getUrl() != null) {
                    result.append("  HTTP URL: ").append(status.getUrl()).append("\n");
                }
                result.append("  Replicas: ").append(status.getReplicas()).append("\n");

                if (status.getConditions() != null) {
                    result.append("  Conditions:\n");
                    for (var condition : status.getConditions()) {
                        result.append("    - ").append(condition.getType())
                                .append(": ").append(condition.getStatus());
                        if (condition.getReason() != null) {
                            result.append(" (").append(condition.getReason()).append(")");
                        }
                        if (condition.getMessage() != null) {
                            result.append("\n      Message: ").append(condition.getMessage());
                        }
                        result.append("\n");
                    }
                }

                result.append("  Observed Generation: ").append(status.getObservedGeneration()).append("\n");
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error describing bridge: " + e.getMessage());
        }
    }
}
