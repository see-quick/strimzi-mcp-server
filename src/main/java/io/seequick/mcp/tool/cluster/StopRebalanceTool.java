package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.rebalance.KafkaRebalance;
import io.strimzi.api.kafka.model.rebalance.KafkaRebalanceList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.HashMap;

/**
 * Tool to stop an in-progress KafkaRebalance operation.
 */
public class StopRebalanceTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaRebalance resource to stop"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the rebalance"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    private static final String REBALANCE_ANNOTATION = "strimzi.io/rebalance";

    public StopRebalanceTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "stop_rebalance";
    }

    @Override
    protected String getDescription() {
        return "Stop an in-progress KafkaRebalance operation (Cruise Control will stop partition movements)";
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

            KafkaRebalance rebalance = kubernetesClient.resources(KafkaRebalance.class, KafkaRebalanceList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (rebalance == null) {
                return error("KafkaRebalance not found: " + namespace + "/" + name);
            }

            // Check current state
            String currentState = "Unknown";
            if (rebalance.getStatus() != null && rebalance.getStatus().getConditions() != null) {
                for (var condition : rebalance.getStatus().getConditions()) {
                    if ("True".equals(condition.getStatus())) {
                        currentState = condition.getType();
                        break;
                    }
                }
            }

            // Apply the stop annotation
            kubernetesClient.resources(KafkaRebalance.class, KafkaRebalanceList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .edit(r -> {
                        if (r.getMetadata().getAnnotations() == null) {
                            r.getMetadata().setAnnotations(new HashMap<>());
                        }
                        r.getMetadata().getAnnotations().put(REBALANCE_ANNOTATION, "stop");
                        return r;
                    });

            StringBuilder result = new StringBuilder();
            result.append("Stopped KafkaRebalance: ").append(namespace).append("/").append(name).append("\n");
            result.append("Previous state: ").append(currentState).append("\n\n");
            result.append("Cruise Control will stop the rebalancing operation.\n");
            result.append("Note: Partition movements that have already started will complete.\n");
            result.append("Use describe_rebalance to check the final state.");

            return success(result.toString());
        } catch (Exception e) {
            return error("Error stopping rebalance: " + e.getMessage());
        }
    }
}
