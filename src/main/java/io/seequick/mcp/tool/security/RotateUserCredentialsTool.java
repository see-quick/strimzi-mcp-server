package io.seequick.mcp.tool.security;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.user.KafkaUser;
import io.strimzi.api.kafka.model.user.KafkaUserList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.time.Instant;
import java.util.HashMap;

/**
 * Tool to trigger credential rotation for a KafkaUser.
 */
public class RotateUserCredentialsTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaUser resource"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the user"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    private static final String ROTATION_ANNOTATION = "strimzi.io/force-password-renewal";

    public RotateUserCredentialsTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "rotate_user_credentials";
    }

    @Override
    protected String getDescription() {
        return "Trigger credential rotation for a KafkaUser (forces new password/certificate generation)";
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

            KafkaUser user = kubernetesClient.resources(KafkaUser.class, KafkaUserList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (user == null) {
                return error("KafkaUser not found: " + namespace + "/" + name);
            }

            // Determine auth type
            String authType = "unknown";
            if (user.getSpec() != null && user.getSpec().getAuthentication() != null) {
                authType = user.getSpec().getAuthentication().getType();
            }

            // Apply the rotation annotation
            String timestamp = Instant.now().toString();
            kubernetesClient.resources(KafkaUser.class, KafkaUserList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .edit(u -> {
                        if (u.getMetadata().getAnnotations() == null) {
                            u.getMetadata().setAnnotations(new HashMap<>());
                        }
                        u.getMetadata().getAnnotations().put(ROTATION_ANNOTATION, timestamp);
                        return u;
                    });

            StringBuilder result = new StringBuilder();
            result.append("Triggered credential rotation for KafkaUser: ").append(namespace).append("/").append(name).append("\n");
            result.append("Authentication type: ").append(authType).append("\n\n");

            if ("scram-sha-512".equalsIgnoreCase(authType)) {
                result.append("The User Operator will generate a new SCRAM password.\n");
                result.append("The new password will be stored in secret: ").append(name).append("\n");
            } else if ("tls".equalsIgnoreCase(authType)) {
                result.append("The User Operator will generate a new TLS certificate.\n");
                result.append("The new certificate will be stored in secret: ").append(name).append("\n");
            }

            result.append("\nIMPORTANT: Update any clients using the old credentials.\n");
            result.append("Use get_user_credentials to retrieve the new credentials.");

            return success(result.toString());
        } catch (Exception e) {
            return error("Error rotating credentials: " + e.getMessage());
        }
    }
}
