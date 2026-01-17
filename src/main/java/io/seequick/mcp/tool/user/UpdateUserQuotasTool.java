package io.seequick.mcp.tool.user;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.user.KafkaUser;
import io.strimzi.api.kafka.model.user.KafkaUserList;
import io.strimzi.api.kafka.model.user.KafkaUserQuotas;
import io.seequick.mcp.tool.AbstractStrimziTool;

/**
 * Tool to update quotas for a KafkaUser.
 */
public class UpdateUserQuotasTool extends AbstractStrimziTool {

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
                    "producerByteRate": {
                        "type": "integer",
                        "description": "Producer quota in bytes per second. Set to 0 to remove quota."
                    },
                    "consumerByteRate": {
                        "type": "integer",
                        "description": "Consumer quota in bytes per second. Set to 0 to remove quota."
                    },
                    "requestPercentage": {
                        "type": "integer",
                        "description": "Request percentage quota (0-100). Set to 0 to remove quota."
                    },
                    "controllerMutationRate": {
                        "type": "number",
                        "description": "Controller mutation rate quota. Set to 0 to remove quota."
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    public UpdateUserQuotasTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "update_user_quotas";
    }

    @Override
    protected String getDescription() {
        return "Update quotas (producer/consumer byte rates, request percentage) for a KafkaUser";
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
            Integer producerByteRate = getOptionalIntArg(args, "producerByteRate");
            Integer consumerByteRate = getOptionalIntArg(args, "consumerByteRate");
            Integer requestPercentage = getOptionalIntArg(args, "requestPercentage");
            Double controllerMutationRate = args.arguments().get("controllerMutationRate") != null ?
                    ((Number) args.arguments().get("controllerMutationRate")).doubleValue() : null;

            KafkaUser user = kubernetesClient.resources(KafkaUser.class, KafkaUserList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (user == null) {
                return error("KafkaUser not found: " + namespace + "/" + name);
            }

            // Get existing quotas
            KafkaUserQuotas existingQuotas = user.getSpec().getQuotas();

            // Update the user
            kubernetesClient.resources(KafkaUser.class, KafkaUserList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .edit(u -> {
                        KafkaUserQuotas quotas = u.getSpec().getQuotas();
                        if (quotas == null) {
                            quotas = new KafkaUserQuotas();
                        }

                        if (producerByteRate != null) {
                            quotas.setProducerByteRate(producerByteRate > 0 ? producerByteRate : null);
                        }
                        if (consumerByteRate != null) {
                            quotas.setConsumerByteRate(consumerByteRate > 0 ? consumerByteRate : null);
                        }
                        if (requestPercentage != null) {
                            quotas.setRequestPercentage(requestPercentage > 0 ? requestPercentage : null);
                        }
                        if (controllerMutationRate != null) {
                            quotas.setControllerMutationRate(controllerMutationRate > 0 ? controllerMutationRate : null);
                        }

                        // If all quotas are null, remove the quotas object
                        if (quotas.getProducerByteRate() == null &&
                            quotas.getConsumerByteRate() == null &&
                            quotas.getRequestPercentage() == null &&
                            quotas.getControllerMutationRate() == null) {
                            u.getSpec().setQuotas(null);
                        } else {
                            u.getSpec().setQuotas(quotas);
                        }

                        return u;
                    });

            StringBuilder result = new StringBuilder();
            result.append("Updated quotas for KafkaUser: ").append(namespace).append("/").append(name).append("\n\n");

            result.append("Previous quotas:\n");
            if (existingQuotas != null) {
                if (existingQuotas.getProducerByteRate() != null) {
                    result.append("  Producer Byte Rate: ").append(existingQuotas.getProducerByteRate()).append(" bytes/sec\n");
                }
                if (existingQuotas.getConsumerByteRate() != null) {
                    result.append("  Consumer Byte Rate: ").append(existingQuotas.getConsumerByteRate()).append(" bytes/sec\n");
                }
                if (existingQuotas.getRequestPercentage() != null) {
                    result.append("  Request Percentage: ").append(existingQuotas.getRequestPercentage()).append("%\n");
                }
                if (existingQuotas.getControllerMutationRate() != null) {
                    result.append("  Controller Mutation Rate: ").append(existingQuotas.getControllerMutationRate()).append("/sec\n");
                }
            } else {
                result.append("  (none)\n");
            }

            result.append("\nNew quotas:\n");
            if (producerByteRate != null && producerByteRate > 0) {
                result.append("  Producer Byte Rate: ").append(producerByteRate).append(" bytes/sec\n");
            }
            if (consumerByteRate != null && consumerByteRate > 0) {
                result.append("  Consumer Byte Rate: ").append(consumerByteRate).append(" bytes/sec\n");
            }
            if (requestPercentage != null && requestPercentage > 0) {
                result.append("  Request Percentage: ").append(requestPercentage).append("%\n");
            }
            if (controllerMutationRate != null && controllerMutationRate > 0) {
                result.append("  Controller Mutation Rate: ").append(controllerMutationRate).append("/sec\n");
            }

            result.append("\nThe User Operator will apply changes shortly.");

            return success(result.toString());
        } catch (Exception e) {
            return error("Error updating user quotas: " + e.getMessage());
        }
    }
}
