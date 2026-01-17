package io.seequick.mcp.tool.user;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.user.KafkaUser;
import io.strimzi.api.kafka.model.user.KafkaUserList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.Map;

/**
 * Tool to list Strimzi KafkaUser resources.
 */
public class ListUsersTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace to list users from. If not specified, lists from all namespaces."
                    },
                    "kafkaCluster": {
                        "type": "string",
                        "description": "Filter users by Kafka cluster name (matches strimzi.io/cluster label)"
                    }
                }
            }
            """;

    public ListUsersTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "list_users";
    }

    @Override
    protected String getDescription() {
        return "List Strimzi KafkaUser resources";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    protected CallToolResult execute(Map<String, Object> args) {
        try {
            String namespace = getStringArg(args, "namespace");
            String kafkaCluster = getStringArg(args, "kafkaCluster");

            KafkaUserList userList = listUsers(namespace, kafkaCluster);

            StringBuilder result = new StringBuilder();
            result.append("Found ").append(userList.getItems().size()).append(" KafkaUser(s):\n\n");

            for (KafkaUser user : userList.getItems()) {
                result.append("- ").append(user.getMetadata().getNamespace())
                        .append("/").append(user.getMetadata().getName());

                // Authentication type
                var spec = user.getSpec();
                if (spec != null && spec.getAuthentication() != null) {
                    result.append(" [auth: ").append(spec.getAuthentication().getType()).append("]");
                }

                // Authorization
                if (spec != null && spec.getAuthorization() != null) {
                    result.append(" [authz: ").append(spec.getAuthorization().getType()).append("]");
                }

                // Ready status
                if (user.getStatus() != null && user.getStatus().getConditions() != null) {
                    var readyCondition = user.getStatus().getConditions().stream()
                            .filter(c -> "Ready".equals(c.getType()))
                            .findFirst();
                    readyCondition.ifPresent(c ->
                            result.append(" [Ready: ").append(c.getStatus()).append("]")
                    );
                }

                // Cluster label
                var labels = user.getMetadata().getLabels();
                if (labels != null && labels.containsKey("strimzi.io/cluster")) {
                    result.append(" -> ").append(labels.get("strimzi.io/cluster"));
                }

                result.append("\n");
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error listing users: " + e.getMessage());
        }
    }

    private KafkaUserList listUsers(String namespace, String kafkaCluster) {
        if (namespace != null && !namespace.isEmpty()) {
            var resource = kubernetesClient.resources(KafkaUser.class, KafkaUserList.class)
                    .inNamespace(namespace);
            if (kafkaCluster != null && !kafkaCluster.isEmpty()) {
                return resource.withLabel("strimzi.io/cluster", kafkaCluster).list();
            }
            return resource.list();
        } else {
            var resource = kubernetesClient.resources(KafkaUser.class, KafkaUserList.class)
                    .inAnyNamespace();
            if (kafkaCluster != null && !kafkaCluster.isEmpty()) {
                return resource.withLabel("strimzi.io/cluster", kafkaCluster).list();
            }
            return resource.list();
        }
    }
}
