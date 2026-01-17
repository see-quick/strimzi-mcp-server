package io.seequick.mcp.tool.user;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.user.KafkaUser;
import io.strimzi.api.kafka.model.user.KafkaUserList;
import io.strimzi.api.kafka.model.user.KafkaUserAuthorizationSimple;
import io.strimzi.api.kafka.model.user.acl.AclRule;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.List;

/**
 * Tool to list ACL rules for a KafkaUser in a readable format.
 */
public class ListUserAclsTool extends AbstractStrimziTool {

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

    public ListUserAclsTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "list_user_acls";
    }

    @Override
    protected String getDescription() {
        return "List all ACL rules for a KafkaUser in a detailed, readable format";
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

            StringBuilder result = new StringBuilder();
            result.append("ACLs for KafkaUser: ").append(namespace).append("/").append(name).append("\n\n");

            if (user.getSpec().getAuthorization() == null) {
                result.append("No authorization configured for this user.\n");
                result.append("Use update_user_acls to add ACL rules.");
                return success(result.toString());
            }

            if (!(user.getSpec().getAuthorization() instanceof KafkaUserAuthorizationSimple simpleAuth)) {
                result.append("Authorization type: ").append(user.getSpec().getAuthorization().getType()).append("\n");
                result.append("(ACL details not available for this authorization type)");
                return success(result.toString());
            }

            List<AclRule> acls = simpleAuth.getAcls();
            if (acls == null || acls.isEmpty()) {
                result.append("No ACL rules defined.\n");
                result.append("Use update_user_acls to add ACL rules.");
                return success(result.toString());
            }

            result.append("Total ACL rules: ").append(acls.size()).append("\n\n");

            int ruleNum = 1;
            for (AclRule acl : acls) {
                result.append("Rule ").append(ruleNum++).append(":\n");
                result.append("  Type: ").append(acl.getType() != null ? acl.getType() : "ALLOW").append("\n");

                if (acl.getResource() != null) {
                    result.append("  Resource:\n");
                    result.append("    Type: ").append(acl.getResource().getType()).append("\n");
                }

                if (acl.getOperations() != null && !acl.getOperations().isEmpty()) {
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

            // Summary by resource type
            result.append("Summary by resource type:\n");
            long topicRules = acls.stream()
                    .filter(a -> a.getResource() != null && a.getResource().getType() != null &&
                            "topic".equalsIgnoreCase(a.getResource().getType().toString()))
                    .count();
            long groupRules = acls.stream()
                    .filter(a -> a.getResource() != null && a.getResource().getType() != null &&
                            "group".equalsIgnoreCase(a.getResource().getType().toString()))
                    .count();
            long clusterRules = acls.stream()
                    .filter(a -> a.getResource() != null && a.getResource().getType() != null &&
                            "cluster".equalsIgnoreCase(a.getResource().getType().toString()))
                    .count();
            long txnRules = acls.stream()
                    .filter(a -> a.getResource() != null && a.getResource().getType() != null &&
                            a.getResource().getType().toString().toLowerCase().contains("transactional"))
                    .count();

            if (topicRules > 0) result.append("  Topic rules: ").append(topicRules).append("\n");
            if (groupRules > 0) result.append("  Group rules: ").append(groupRules).append("\n");
            if (clusterRules > 0) result.append("  Cluster rules: ").append(clusterRules).append("\n");
            if (txnRules > 0) result.append("  TransactionalId rules: ").append(txnRules).append("\n");

            return success(result.toString());
        } catch (Exception e) {
            return error("Error listing user ACLs: " + e.getMessage());
        }
    }
}
