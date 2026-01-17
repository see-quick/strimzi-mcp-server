package io.seequick.mcp.tool.user;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.user.KafkaUser;
import io.strimzi.api.kafka.model.user.KafkaUserList;
import io.strimzi.api.kafka.model.user.KafkaUserAuthorizationSimple;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.Map;

/**
 * Tool to get detailed information about a KafkaUser.
 */
public class DescribeUserTool extends AbstractStrimziTool {

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

    public DescribeUserTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "describe_user";
    }

    @Override
    protected String getDescription() {
        return "Get detailed information about a KafkaUser including authentication, authorization, and quotas";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    protected CallToolResult execute(Map<String, Object> args) {
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
            result.append("KafkaUser: ").append(namespace).append("/").append(name).append("\n\n");

            // Cluster label
            var labels = user.getMetadata().getLabels();
            if (labels != null && labels.containsKey("strimzi.io/cluster")) {
                result.append("Kafka Cluster: ").append(labels.get("strimzi.io/cluster")).append("\n");
            }

            var spec = user.getSpec();
            if (spec != null) {
                // Authentication
                result.append("\nAuthentication:\n");
                if (spec.getAuthentication() != null) {
                    result.append("  Type: ").append(spec.getAuthentication().getType()).append("\n");
                } else {
                    result.append("  Type: none\n");
                }

                // Authorization (ACLs)
                if (spec.getAuthorization() != null) {
                    result.append("\nAuthorization:\n");
                    result.append("  Type: ").append(spec.getAuthorization().getType()).append("\n");

                    if (spec.getAuthorization() instanceof KafkaUserAuthorizationSimple simpleAuth) {
                        var acls = simpleAuth.getAcls();
                        if (acls != null && !acls.isEmpty()) {
                            result.append("  ACL Rules (").append(acls.size()).append("):\n");
                            for (var acl : acls) {
                                result.append("    - ").append(acl.getType() != null ? acl.getType() : "ALLOW");
                                result.append(" ").append(acl.getOperations());
                                result.append(" on ").append(acl.getResource().getType());
                                result.append("\n");
                            }
                        }
                    }
                }

                // Quotas
                if (spec.getQuotas() != null) {
                    result.append("\nQuotas:\n");
                    var quotas = spec.getQuotas();
                    if (quotas.getProducerByteRate() != null) {
                        result.append("  Producer Byte Rate: ").append(quotas.getProducerByteRate()).append(" bytes/sec\n");
                    }
                    if (quotas.getConsumerByteRate() != null) {
                        result.append("  Consumer Byte Rate: ").append(quotas.getConsumerByteRate()).append(" bytes/sec\n");
                    }
                    if (quotas.getRequestPercentage() != null) {
                        result.append("  Request Percentage: ").append(quotas.getRequestPercentage()).append("%\n");
                    }
                    if (quotas.getControllerMutationRate() != null) {
                        result.append("  Controller Mutation Rate: ").append(quotas.getControllerMutationRate()).append("/sec\n");
                    }
                }
            }

            // Status
            var status = user.getStatus();
            if (status != null) {
                result.append("\nStatus:\n");

                if (status.getUsername() != null) {
                    result.append("  Username: ").append(status.getUsername()).append("\n");
                }
                if (status.getSecret() != null) {
                    result.append("  Secret: ").append(status.getSecret()).append("\n");
                }

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
            return error("Error describing user: " + e.getMessage());
        }
    }
}
