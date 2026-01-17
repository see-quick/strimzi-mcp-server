package io.seequick.mcp.tool.user;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.user.KafkaUser;
import io.strimzi.api.kafka.model.user.KafkaUserList;
import io.strimzi.api.kafka.model.user.KafkaUserAuthorizationSimple;
import io.seequick.mcp.tool.AbstractStrimziTool;

/**
 * Tool to manage ACL rules for a KafkaUser.
 * Currently supports clearing all ACLs. For adding specific ACLs, use kubectl apply with YAML.
 */
public class UpdateUserAclsTool extends AbstractStrimziTool {

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
                    },
                    "action": {
                        "type": "string",
                        "enum": ["clear", "show"],
                        "description": "Action to perform: clear (remove all ACLs) or show (display current ACLs)"
                    }
                },
                "required": ["name", "namespace", "action"]
            }
            """;

    public UpdateUserAclsTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "update_user_acls";
    }

    @Override
    protected String getDescription() {
        return "Show or clear ACL rules for a KafkaUser. Use 'show' to view current ACLs, 'clear' to remove all ACLs.";
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
            String action = getStringArg(args, "action");

            KafkaUser user = kubernetesClient.resources(KafkaUser.class, KafkaUserList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (user == null) {
                return error("KafkaUser not found: " + namespace + "/" + name);
            }

            StringBuilder result = new StringBuilder();

            if ("show".equalsIgnoreCase(action)) {
                result.append("ACLs for KafkaUser: ").append(namespace).append("/").append(name).append("\n\n");

                if (user.getSpec().getAuthorization() == null) {
                    result.append("No authorization configured for this user.\n");
                } else if (user.getSpec().getAuthorization() instanceof KafkaUserAuthorizationSimple simpleAuth) {
                    var acls = simpleAuth.getAcls();
                    if (acls == null || acls.isEmpty()) {
                        result.append("No ACL rules defined.\n");
                    } else {
                        result.append("Total ACL rules: ").append(acls.size()).append("\n\n");
                        int ruleNum = 1;
                        for (var acl : acls) {
                            result.append("Rule ").append(ruleNum++).append(":\n");
                            result.append("  Type: ").append(acl.getType()).append("\n");
                            if (acl.getResource() != null) {
                                result.append("  Resource Type: ").append(acl.getResource().getType()).append("\n");
                            }
                            if (acl.getOperations() != null) {
                                result.append("  Operations: ");
                                result.append(String.join(", ", acl.getOperations().stream()
                                        .map(Enum::name)
                                        .toList()));
                                result.append("\n");
                            }
                            if (acl.getHost() != null) {
                                result.append("  Host: ").append(acl.getHost()).append("\n");
                            }
                            result.append("\n");
                        }
                    }
                } else {
                    result.append("Authorization type: ").append(user.getSpec().getAuthorization().getType()).append("\n");
                }

                result.append("\nTo modify ACLs, use export_resource_yaml to get the YAML and apply changes with kubectl.");

            } else if ("clear".equalsIgnoreCase(action)) {
                // Get existing ACLs count
                int existingCount = 0;
                if (user.getSpec().getAuthorization() instanceof KafkaUserAuthorizationSimple simpleAuth) {
                    if (simpleAuth.getAcls() != null) {
                        existingCount = simpleAuth.getAcls().size();
                    }
                }

                // Clear all ACLs
                kubernetesClient.resources(KafkaUser.class, KafkaUserList.class)
                        .inNamespace(namespace)
                        .withName(name)
                        .edit(u -> {
                            u.getSpec().setAuthorization(null);
                            return u;
                        });

                result.append("Cleared ACLs for KafkaUser: ").append(namespace).append("/").append(name).append("\n");
                result.append("Removed ").append(existingCount).append(" ACL rule(s).\n");
                result.append("\nThe User Operator will apply changes shortly.");

            } else {
                return error("Invalid action: " + action + ". Use 'show' or 'clear'.");
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error managing user ACLs: " + e.getMessage());
        }
    }
}
