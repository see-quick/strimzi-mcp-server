package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.nodepool.KafkaNodePool;
import io.strimzi.api.kafka.model.nodepool.KafkaNodePoolList;
import io.seequick.mcp.tool.AbstractStrimziTool;

/**
 * Tool to list Strimzi KafkaNodePool resources.
 */
public class ListNodePoolsTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace to list node pools from. If not specified, lists from all namespaces."
                    },
                    "kafkaCluster": {
                        "type": "string",
                        "description": "Filter node pools by Kafka cluster name (matches strimzi.io/cluster label)"
                    }
                }
            }
            """;

    public ListNodePoolsTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "list_node_pools";
    }

    @Override
    protected String getDescription() {
        return "List Strimzi KafkaNodePool resources";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    protected CallToolResult execute(McpSchema.CallToolRequest args) {
        try {
            String namespace = getStringArg(args, "namespace");
            String kafkaCluster = getStringArg(args, "kafkaCluster");

            KafkaNodePoolList poolList = listNodePools(namespace, kafkaCluster);

            StringBuilder result = new StringBuilder();
            result.append("Found ").append(poolList.getItems().size()).append(" KafkaNodePool(s):\n\n");

            for (KafkaNodePool pool : poolList.getItems()) {
                result.append("- ").append(pool.getMetadata().getNamespace())
                        .append("/").append(pool.getMetadata().getName());

                var spec = pool.getSpec();
                if (spec != null) {
                    result.append(" [replicas: ").append(spec.getReplicas());
                    if (spec.getRoles() != null) {
                        result.append(", roles: ").append(spec.getRoles());
                    }
                    result.append("]");
                }

                // Status
                var status = pool.getStatus();
                if (status != null) {
                    if (status.getNodeIds() != null) {
                        result.append(" nodeIds: ").append(status.getNodeIds());
                    }
                }

                // Cluster label
                var labels = pool.getMetadata().getLabels();
                if (labels != null && labels.containsKey("strimzi.io/cluster")) {
                    result.append(" -> ").append(labels.get("strimzi.io/cluster"));
                }

                result.append("\n");
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error listing node pools: " + e.getMessage());
        }
    }

    private KafkaNodePoolList listNodePools(String namespace, String kafkaCluster) {
        if (namespace != null && !namespace.isEmpty()) {
            var resource = kubernetesClient.resources(KafkaNodePool.class, KafkaNodePoolList.class)
                    .inNamespace(namespace);
            if (kafkaCluster != null && !kafkaCluster.isEmpty()) {
                return resource.withLabel("strimzi.io/cluster", kafkaCluster).list();
            }
            return resource.list();
        } else {
            var resource = kubernetesClient.resources(KafkaNodePool.class, KafkaNodePoolList.class)
                    .inAnyNamespace();
            if (kafkaCluster != null && !kafkaCluster.isEmpty()) {
                return resource.withLabel("strimzi.io/cluster", kafkaCluster).list();
            }
            return resource.list();
        }
    }
}
