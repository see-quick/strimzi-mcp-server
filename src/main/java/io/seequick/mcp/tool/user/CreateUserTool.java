package io.seequick.mcp.tool.user;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.strimzi.api.kafka.model.user.KafkaUser;
import io.strimzi.api.kafka.model.user.KafkaUserBuilder;
import io.strimzi.api.kafka.model.user.KafkaUserList;
import io.strimzi.api.kafka.model.user.KafkaUserScramSha512ClientAuthentication;
import io.strimzi.api.kafka.model.user.KafkaUserTlsClientAuthentication;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.Map;

/**
 * Tool to create a new KafkaUser resource.
 */
public class CreateUserTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaUser resource to create"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace to create the user in"
                    },
                    "kafkaCluster": {
                        "type": "string",
                        "description": "Name of the Kafka cluster (strimzi.io/cluster label)"
                    },
                    "authentication": {
                        "type": "string",
                        "enum": ["scram-sha-512", "tls"],
                        "description": "Authentication type (default: scram-sha-512)"
                    },
                    "producerByteRate": {
                        "type": "integer",
                        "description": "Producer quota in bytes per second"
                    },
                    "consumerByteRate": {
                        "type": "integer",
                        "description": "Consumer quota in bytes per second"
                    }
                },
                "required": ["name", "namespace", "kafkaCluster"]
            }
            """;

    public CreateUserTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "create_user";
    }

    @Override
    protected String getDescription() {
        return "Create a new KafkaUser resource managed by the User Operator";
    }

    @Override
    protected String getInputSchema() {
        return SCHEMA;
    }

    @Override
    protected CallToolResult execute(Map<String, Object> args) {
        try {
            String name = getStringArg(args, "name");
            String namespace = getStringArg(args, "namespace");
            String kafkaCluster = getStringArg(args, "kafkaCluster");
            String authType = getStringArg(args, "authentication");
            if (authType == null) {
                authType = "scram-sha-512";
            }
            Integer producerByteRate = getOptionalIntArg(args, "producerByteRate");
            Integer consumerByteRate = getOptionalIntArg(args, "consumerByteRate");

            // Check if user already exists
            KafkaUser existing = kubernetesClient.resources(KafkaUser.class, KafkaUserList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (existing != null) {
                return error("KafkaUser already exists: " + namespace + "/" + name);
            }

            // Build the user
            var userBuilder = new KafkaUserBuilder()
                    .withNewMetadata()
                        .withName(name)
                        .withNamespace(namespace)
                        .addToLabels("strimzi.io/cluster", kafkaCluster)
                    .endMetadata()
                    .withNewSpec()
                    .endSpec();

            // Set authentication
            if ("tls".equals(authType)) {
                userBuilder.editSpec()
                        .withAuthentication(new KafkaUserTlsClientAuthentication())
                        .endSpec();
            } else {
                userBuilder.editSpec()
                        .withAuthentication(new KafkaUserScramSha512ClientAuthentication())
                        .endSpec();
            }

            // Set quotas if provided
            if (producerByteRate != null || consumerByteRate != null) {
                userBuilder.editSpec()
                        .withNewQuotas()
                            .withProducerByteRate(producerByteRate)
                            .withConsumerByteRate(consumerByteRate)
                        .endQuotas()
                        .endSpec();
            }

            KafkaUser user = userBuilder.build();

            kubernetesClient.resources(KafkaUser.class, KafkaUserList.class)
                    .inNamespace(namespace)
                    .resource(user)
                    .create();

            StringBuilder result = new StringBuilder();
            result.append("Created KafkaUser: ").append(namespace).append("/").append(name).append("\n");
            result.append("  Kafka Cluster: ").append(kafkaCluster).append("\n");
            result.append("  Authentication: ").append(authType).append("\n");
            if (producerByteRate != null) {
                result.append("  Producer Byte Rate: ").append(producerByteRate).append(" bytes/sec\n");
            }
            if (consumerByteRate != null) {
                result.append("  Consumer Byte Rate: ").append(consumerByteRate).append(" bytes/sec\n");
            }
            result.append("\nThe User Operator will create the user in Kafka shortly.");
            result.append("\nCredentials will be stored in secret: ").append(name);

            return success(result.toString());
        } catch (Exception e) {
            return error("Error creating user: " + e.getMessage());
        }
    }
}
