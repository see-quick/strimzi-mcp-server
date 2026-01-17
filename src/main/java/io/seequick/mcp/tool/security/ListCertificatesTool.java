package io.seequick.mcp.tool.security;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.kafka.Kafka;
import io.strimzi.api.kafka.model.kafka.KafkaList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.List;
import java.util.Map;

/**
 * Tool to list certificates for a Kafka cluster.
 */
public class ListCertificatesTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "kafkaCluster": {
                        "type": "string",
                        "description": "Name of the Kafka cluster"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the Kafka cluster"
                    }
                },
                "required": ["kafkaCluster", "namespace"]
            }
            """;

    public ListCertificatesTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "list_certificates";
    }

    @Override
    protected String getDescription() {
        return "List TLS certificates for a Kafka cluster (CA certificates, listener certificates)";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    protected CallToolResult execute(McpSchema.CallToolRequest args) {
        try {
            String kafkaCluster = getStringArg(args, "kafkaCluster");
            String namespace = getStringArg(args, "namespace");

            // Verify Kafka cluster exists
            Kafka kafka = kubernetesClient.resources(Kafka.class, KafkaList.class)
                    .inNamespace(namespace)
                    .withName(kafkaCluster)
                    .get();

            if (kafka == null) {
                return error("Kafka cluster not found: " + namespace + "/" + kafkaCluster);
            }

            StringBuilder result = new StringBuilder();
            result.append("Certificates for Kafka Cluster: ").append(namespace).append("/").append(kafkaCluster).append("\n");
            result.append("═".repeat(60)).append("\n\n");

            // Cluster CA Secret
            String clusterCaSecretName = kafkaCluster + "-cluster-ca-cert";
            Secret clusterCaSecret = kubernetesClient.secrets()
                    .inNamespace(namespace)
                    .withName(clusterCaSecretName)
                    .get();

            result.append("CLUSTER CA CERTIFICATE\n");
            result.append("─".repeat(40)).append("\n");
            if (clusterCaSecret != null) {
                result.append("Secret: ").append(clusterCaSecretName).append("\n");
                Map<String, String> data = clusterCaSecret.getData();
                if (data != null) {
                    result.append("Keys: ").append(String.join(", ", data.keySet())).append("\n");
                }
                var annotations = clusterCaSecret.getMetadata().getAnnotations();
                if (annotations != null) {
                    if (annotations.containsKey("strimzi.io/ca-cert-generation")) {
                        result.append("Generation: ").append(annotations.get("strimzi.io/ca-cert-generation")).append("\n");
                    }
                }
            } else {
                result.append("Not found\n");
            }
            result.append("\n");

            // Clients CA Secret
            String clientsCaSecretName = kafkaCluster + "-clients-ca-cert";
            Secret clientsCaSecret = kubernetesClient.secrets()
                    .inNamespace(namespace)
                    .withName(clientsCaSecretName)
                    .get();

            result.append("CLIENTS CA CERTIFICATE\n");
            result.append("─".repeat(40)).append("\n");
            if (clientsCaSecret != null) {
                result.append("Secret: ").append(clientsCaSecretName).append("\n");
                Map<String, String> data = clientsCaSecret.getData();
                if (data != null) {
                    result.append("Keys: ").append(String.join(", ", data.keySet())).append("\n");
                }
                var annotations = clientsCaSecret.getMetadata().getAnnotations();
                if (annotations != null) {
                    if (annotations.containsKey("strimzi.io/ca-cert-generation")) {
                        result.append("Generation: ").append(annotations.get("strimzi.io/ca-cert-generation")).append("\n");
                    }
                }
            } else {
                result.append("Not found\n");
            }
            result.append("\n");

            // List all Kafka-related secrets
            List<Secret> kafkaSecrets = kubernetesClient.secrets()
                    .inNamespace(namespace)
                    .withLabel("strimzi.io/cluster", kafkaCluster)
                    .list()
                    .getItems();

            List<Secret> certSecrets = kafkaSecrets.stream()
                    .filter(s -> s.getMetadata().getName().contains("-cert") ||
                                s.getMetadata().getName().contains("-crt") ||
                                s.getMetadata().getName().contains("-ca"))
                    .toList();

            if (!certSecrets.isEmpty()) {
                result.append("OTHER CERTIFICATE SECRETS\n");
                result.append("─".repeat(40)).append("\n");
                for (Secret secret : certSecrets) {
                    String secretName = secret.getMetadata().getName();
                    if (!secretName.equals(clusterCaSecretName) && !secretName.equals(clientsCaSecretName)) {
                        result.append("  ").append(secretName).append("\n");
                    }
                }
            }

            result.append("\nTo rotate certificates, update the Kafka resource or use specific strimzi.io annotations.");

            return success(result.toString());
        } catch (Exception e) {
            return error("Error listing certificates: " + e.getMessage());
        }
    }
}
