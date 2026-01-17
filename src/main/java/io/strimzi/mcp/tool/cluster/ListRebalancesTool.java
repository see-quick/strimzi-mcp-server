package io.strimzi.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.strimzi.api.kafka.model.rebalance.KafkaRebalance;
import io.strimzi.api.kafka.model.rebalance.KafkaRebalanceList;
import io.strimzi.mcp.tool.AbstractStrimziTool;

import java.util.Map;

/**
 * Tool to list Strimzi KafkaRebalance resources.
 */
public class ListRebalancesTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace to list rebalances from. If not specified, lists from all namespaces."
                    },
                    "kafkaCluster": {
                        "type": "string",
                        "description": "Filter rebalances by Kafka cluster name (matches strimzi.io/cluster label)"
                    }
                }
            }
            """;

    public ListRebalancesTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "list_rebalances";
    }

    @Override
    protected String getDescription() {
        return "List Strimzi KafkaRebalance resources (Cruise Control rebalancing operations)";
    }

    @Override
    protected String getInputSchema() {
        return SCHEMA;
    }

    @Override
    protected CallToolResult execute(Map<String, Object> args) {
        try {
            String namespace = getStringArg(args, "namespace");
            String kafkaCluster = getStringArg(args, "kafkaCluster");

            KafkaRebalanceList rebalanceList = listRebalances(namespace, kafkaCluster);

            StringBuilder result = new StringBuilder();
            result.append("Found ").append(rebalanceList.getItems().size()).append(" KafkaRebalance(s):\n\n");

            for (KafkaRebalance rebalance : rebalanceList.getItems()) {
                result.append("- ").append(rebalance.getMetadata().getNamespace())
                        .append("/").append(rebalance.getMetadata().getName());

                var spec = rebalance.getSpec();
                if (spec != null) {
                    if (spec.getMode() != null) {
                        result.append(" [mode: ").append(spec.getMode()).append("]");
                    }
                }

                // Status - show current state
                var status = rebalance.getStatus();
                if (status != null && status.getConditions() != null) {
                    // Find the most relevant condition (state)
                    for (var condition : status.getConditions()) {
                        if ("True".equals(condition.getStatus())) {
                            result.append(" [").append(condition.getType()).append("]");
                            break;
                        }
                    }
                    if (status.getSessionId() != null) {
                        result.append(" session: ").append(status.getSessionId());
                    }
                }

                // Cluster label
                var labels = rebalance.getMetadata().getLabels();
                if (labels != null && labels.containsKey("strimzi.io/cluster")) {
                    result.append(" -> ").append(labels.get("strimzi.io/cluster"));
                }

                result.append("\n");
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error listing rebalances: " + e.getMessage());
        }
    }

    private KafkaRebalanceList listRebalances(String namespace, String kafkaCluster) {
        if (namespace != null && !namespace.isEmpty()) {
            var resource = kubernetesClient.resources(KafkaRebalance.class, KafkaRebalanceList.class)
                    .inNamespace(namespace);
            if (kafkaCluster != null && !kafkaCluster.isEmpty()) {
                return resource.withLabel("strimzi.io/cluster", kafkaCluster).list();
            }
            return resource.list();
        } else {
            var resource = kubernetesClient.resources(KafkaRebalance.class, KafkaRebalanceList.class)
                    .inAnyNamespace();
            if (kafkaCluster != null && !kafkaCluster.isEmpty()) {
                return resource.withLabel("strimzi.io/cluster", kafkaCluster).list();
            }
            return resource.list();
        }
    }
}
