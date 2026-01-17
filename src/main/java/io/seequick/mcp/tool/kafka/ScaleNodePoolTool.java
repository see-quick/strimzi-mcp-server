package io.seequick.mcp.tool.kafka;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.nodepool.KafkaNodePool;
import io.strimzi.api.kafka.model.nodepool.KafkaNodePoolList;
import io.seequick.mcp.tool.AbstractStrimziTool;

/**
 * Tool to scale a KafkaNodePool by adjusting the replica count.
 */
public class ScaleNodePoolTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaNodePool to scale"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the KafkaNodePool"
                    },
                    "replicas": {
                        "type": "integer",
                        "description": "Desired number of replicas"
                    }
                },
                "required": ["name", "namespace", "replicas"]
            }
            """;

    public ScaleNodePoolTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "scale_node_pool";
    }

    @Override
    protected String getDescription() {
        return "Scale a KafkaNodePool by adjusting the number of replicas";
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
            int replicas = getIntArg(args, "replicas", -1);

            if (replicas < 0) {
                return error("replicas must be a non-negative integer");
            }

            KafkaNodePool nodePool = kubernetesClient.resources(KafkaNodePool.class, KafkaNodePoolList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (nodePool == null) {
                return error("KafkaNodePool not found: " + namespace + "/" + name);
            }

            int currentReplicas = nodePool.getSpec().getReplicas();

            if (currentReplicas == replicas) {
                return success("KafkaNodePool " + name + " already has " + replicas + " replicas. No change needed.");
            }

            // Scale the node pool
            kubernetesClient.resources(KafkaNodePool.class, KafkaNodePoolList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .edit(np -> {
                        np.getSpec().setReplicas(replicas);
                        return np;
                    });

            StringBuilder result = new StringBuilder();
            result.append("Scaled KafkaNodePool: ").append(namespace).append("/").append(name).append("\n");
            result.append("  Previous replicas: ").append(currentReplicas).append("\n");
            result.append("  New replicas: ").append(replicas).append("\n");

            if (replicas > currentReplicas) {
                result.append("\nThe Cluster Operator will add ").append(replicas - currentReplicas).append(" new broker(s).");
            } else {
                result.append("\nThe Cluster Operator will remove ").append(currentReplicas - replicas).append(" broker(s).");
                result.append("\nNote: Partition data will be migrated before removal.");
            }

            result.append("\nUse describe_node_pool to monitor progress.");

            return success(result.toString());
        } catch (Exception e) {
            return error("Error scaling node pool: " + e.getMessage());
        }
    }
}
