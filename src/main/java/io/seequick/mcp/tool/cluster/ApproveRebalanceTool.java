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
 * Tool to approve a KafkaRebalance proposal for execution.
 */
public class ApproveRebalanceTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaRebalance resource to approve"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the rebalance"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    private static final String APPROVE_ANNOTATION = "strimzi.io/rebalance";

    public ApproveRebalanceTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "approve_rebalance";
    }

    @Override
    protected String getDescription() {
        return "Approve a KafkaRebalance proposal for execution by Cruise Control";
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

            // Only approve if in ProposalReady state
            if (!"ProposalReady".equals(currentState)) {
                return error("KafkaRebalance is not ready to approve. Current state: " + currentState +
                        ". Expected: ProposalReady");
            }

            // Apply the approve annotation
            kubernetesClient.resources(KafkaRebalance.class, KafkaRebalanceList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .edit(r -> {
                        if (r.getMetadata().getAnnotations() == null) {
                            r.getMetadata().setAnnotations(new HashMap<>());
                        }
                        r.getMetadata().getAnnotations().put(APPROVE_ANNOTATION, "approve");
                        return r;
                    });

            StringBuilder result = new StringBuilder();
            result.append("Approved KafkaRebalance: ").append(namespace).append("/").append(name).append("\n\n");
            result.append("Cruise Control will now execute the optimization proposal.\n");
            result.append("Use describe_rebalance to monitor progress.");

            return success(result.toString());
        } catch (Exception e) {
            return error("Error approving rebalance: " + e.getMessage());
        }
    }
}
