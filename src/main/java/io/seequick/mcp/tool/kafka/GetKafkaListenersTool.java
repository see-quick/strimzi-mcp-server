package io.seequick.mcp.tool.kafka;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.kafka.Kafka;
import io.strimzi.api.kafka.model.kafka.KafkaList;
import io.strimzi.api.kafka.model.kafka.listener.GenericKafkaListener;
import io.strimzi.api.kafka.model.kafka.listener.ListenerStatus;
import io.seequick.mcp.tool.AbstractStrimziTool;

/**
 * Tool to list Kafka listener addresses for client connections.
 */
public class GetKafkaListenersTool extends AbstractStrimziTool {

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
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    public GetKafkaListenersTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "get_kafka_listeners";
    }

    @Override
    protected String getDescription() {
        return "Get Kafka listener addresses for client connections, including bootstrap addresses and per-broker addresses";
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

            Kafka kafka = kubernetesClient.resources(Kafka.class, KafkaList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (kafka == null) {
                return error("Kafka cluster not found: " + namespace + "/" + name);
            }

            StringBuilder result = new StringBuilder();
            result.append("Kafka Cluster: ").append(namespace).append("/").append(name).append("\n\n");

            // Spec - listener configuration
            var spec = kafka.getSpec();
            if (spec.getKafka() != null && spec.getKafka().getListeners() != null) {
                result.append("Configured Listeners:\n");
                for (GenericKafkaListener listener : spec.getKafka().getListeners()) {
                    result.append("  ").append(listener.getName()).append(":\n");
                    result.append("    Type: ").append(listener.getType()).append("\n");
                    result.append("    Port: ").append(listener.getPort()).append("\n");
                    // TLS is implied by listener configuration in newer Strimzi versions
                    if (listener.getConfiguration() != null) {
                        result.append("    Has custom configuration: yes\n");
                    }
                }
            }

            // Status - actual addresses
            var status = kafka.getStatus();
            if (status != null && status.getListeners() != null && !status.getListeners().isEmpty()) {
                result.append("\nListener Addresses:\n");
                for (ListenerStatus listenerStatus : status.getListeners()) {
                    result.append("  ").append(listenerStatus.getName()).append(":\n");

                    if (listenerStatus.getBootstrapServers() != null) {
                        result.append("    Bootstrap: ").append(listenerStatus.getBootstrapServers()).append("\n");
                    }

                    if (listenerStatus.getAddresses() != null && !listenerStatus.getAddresses().isEmpty()) {
                        result.append("    Addresses:\n");
                        for (var address : listenerStatus.getAddresses()) {
                            result.append("      - ");
                            if (address.getHost() != null) {
                                result.append(address.getHost());
                            }
                            if (address.getPort() != null) {
                                result.append(":").append(address.getPort());
                            }
                            result.append("\n");
                        }
                    }

                    // Certificate info if available
                    if (listenerStatus.getCertificates() != null && !listenerStatus.getCertificates().isEmpty()) {
                        result.append("    Certificates available: Yes\n");
                    }
                }
            } else {
                result.append("\nNo listener addresses available yet. The cluster may still be starting.\n");
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error getting listeners: " + e.getMessage());
        }
    }
}
