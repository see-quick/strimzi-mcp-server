package io.seequick.mcp.tool.user;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.user.KafkaUser;
import io.strimzi.api.kafka.model.user.KafkaUserList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.Base64;

/**
 * Tool to get credentials for a KafkaUser from the generated Kubernetes Secret.
 */
public class GetUserCredentialsTool extends AbstractStrimziTool {

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

    public GetUserCredentialsTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "get_user_credentials";
    }

    @Override
    protected String getDescription() {
        return "Get credentials for a KafkaUser from the generated Kubernetes Secret";
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

            // First get the KafkaUser to find the secret name
            KafkaUser user = kubernetesClient.resources(KafkaUser.class, KafkaUserList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (user == null) {
                return error("KafkaUser not found: " + namespace + "/" + name);
            }

            // Get secret name from status (or default to user name)
            String secretName = name;
            if (user.getStatus() != null && user.getStatus().getSecret() != null) {
                secretName = user.getStatus().getSecret();
            }

            // Get the secret
            var secret = kubernetesClient.secrets()
                    .inNamespace(namespace)
                    .withName(secretName)
                    .get();

            if (secret == null) {
                return error("Credentials secret not found: " + namespace + "/" + secretName +
                        "\nThe User Operator may not have created it yet. Check user status.");
            }

            StringBuilder result = new StringBuilder();
            result.append("Credentials for KafkaUser: ").append(namespace).append("/").append(name).append("\n");
            result.append("Secret: ").append(secretName).append("\n\n");

            var data = secret.getData();
            if (data != null) {
                // SCRAM-SHA-512 credentials
                if (data.containsKey("password")) {
                    String password = new String(Base64.getDecoder().decode(data.get("password")));
                    result.append("Authentication: SCRAM-SHA-512\n");
                    result.append("Username: ").append(name).append("\n");
                    result.append("Password: ").append(password).append("\n");
                }

                if (data.containsKey("sasl.jaas.config")) {
                    String jaasConfig = new String(Base64.getDecoder().decode(data.get("sasl.jaas.config")));
                    result.append("\nJAAS Config:\n").append(jaasConfig).append("\n");
                }

                // TLS credentials
                if (data.containsKey("user.crt")) {
                    result.append("Authentication: TLS\n");
                    result.append("Certificate: Available in secret key 'user.crt'\n");
                    result.append("Private Key: Available in secret key 'user.key'\n");

                    // Show certificate info (first few lines)
                    String cert = new String(Base64.getDecoder().decode(data.get("user.crt")));
                    String[] certLines = cert.split("\n");
                    result.append("\nCertificate (first 5 lines):\n");
                    for (int i = 0; i < Math.min(5, certLines.length); i++) {
                        result.append("  ").append(certLines[i]).append("\n");
                    }
                    if (certLines.length > 5) {
                        result.append("  ...\n");
                    }
                }

                // CA certificate if present
                if (data.containsKey("ca.crt")) {
                    result.append("\nCA Certificate: Available in secret key 'ca.crt'\n");
                }
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error getting user credentials: " + e.getMessage());
        }
    }
}
