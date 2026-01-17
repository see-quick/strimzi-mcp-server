package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.rebalance.KafkaRebalance;
import io.strimzi.api.kafka.model.rebalance.KafkaRebalanceBuilder;
import io.strimzi.api.kafka.model.rebalance.KafkaRebalanceList;
import io.strimzi.api.kafka.model.rebalance.KafkaRebalanceMode;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.List;

/**
 * Tool to create a new KafkaRebalance resource for Cruise Control.
 */
public class CreateRebalanceTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaRebalance resource to create"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace to create the rebalance in"
                    },
                    "kafkaCluster": {
                        "type": "string",
                        "description": "Name of the Kafka cluster (strimzi.io/cluster label)"
                    },
                    "mode": {
                        "type": "string",
                        "enum": ["full", "add-brokers", "remove-brokers"],
                        "description": "Rebalance mode (default: full)"
                    },
                    "brokers": {
                        "type": "array",
                        "items": { "type": "integer" },
                        "description": "Broker IDs for add-brokers or remove-brokers mode"
                    },
                    "goals": {
                        "type": "array",
                        "items": { "type": "string" },
                        "description": "Optional: list of optimization goals"
                    },
                    "skipHardGoalCheck": {
                        "type": "boolean",
                        "description": "Skip hard goal check (default: false)"
                    },
                    "rebalanceDisk": {
                        "type": "boolean",
                        "description": "Rebalance disk usage (default: false)"
                    },
                    "concurrentPartitionMovementsPerBroker": {
                        "type": "integer",
                        "description": "Max concurrent partition movements per broker"
                    },
                    "concurrentIntraBrokerPartitionMovements": {
                        "type": "integer",
                        "description": "Max concurrent intra-broker partition movements"
                    },
                    "concurrentLeaderMovements": {
                        "type": "integer",
                        "description": "Max concurrent leader movements"
                    }
                },
                "required": ["name", "namespace", "kafkaCluster"]
            }
            """;

    public CreateRebalanceTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "create_rebalance";
    }

    @Override
    protected String getDescription() {
        return "Create a new KafkaRebalance resource to trigger Cruise Control optimization";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected CallToolResult execute(McpSchema.CallToolRequest args) {
        try {
            String name = getStringArg(args, "name");
            String namespace = getStringArg(args, "namespace");
            String kafkaCluster = getStringArg(args, "kafkaCluster");
            String mode = getStringArg(args, "mode");
            List<Integer> brokers = (List<Integer>) args.arguments().get("brokers");
            List<String> goals = (List<String>) args.arguments().get("goals");
            Boolean skipHardGoalCheck = (Boolean) args.arguments().get("skipHardGoalCheck");
            Boolean rebalanceDisk = (Boolean) args.arguments().get("rebalanceDisk");
            Integer concurrentPartition = getOptionalIntArg(args, "concurrentPartitionMovementsPerBroker");
            Integer concurrentIntraBroker = getOptionalIntArg(args, "concurrentIntraBrokerPartitionMovements");
            Integer concurrentLeader = getOptionalIntArg(args, "concurrentLeaderMovements");

            // Check if rebalance already exists
            KafkaRebalance existing = kubernetesClient.resources(KafkaRebalance.class, KafkaRebalanceList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (existing != null) {
                return error("KafkaRebalance already exists: " + namespace + "/" + name);
            }

            // Build the rebalance
            var rebalanceBuilder = new KafkaRebalanceBuilder()
                    .withNewMetadata()
                        .withName(name)
                        .withNamespace(namespace)
                        .addToLabels("strimzi.io/cluster", kafkaCluster)
                    .endMetadata()
                    .withNewSpec()
                    .endSpec();

            // Set mode
            if (mode != null) {
                KafkaRebalanceMode rebalanceMode = switch (mode.toLowerCase()) {
                    case "add-brokers" -> KafkaRebalanceMode.ADD_BROKERS;
                    case "remove-brokers" -> KafkaRebalanceMode.REMOVE_BROKERS;
                    default -> KafkaRebalanceMode.FULL;
                };
                rebalanceBuilder.editSpec().withMode(rebalanceMode).endSpec();
            }

            // Set brokers for add/remove modes
            if (brokers != null && !brokers.isEmpty()) {
                rebalanceBuilder.editSpec().withBrokers(brokers).endSpec();
            }

            // Set goals
            if (goals != null && !goals.isEmpty()) {
                rebalanceBuilder.editSpec().withGoals(goals).endSpec();
            }

            // Set options
            if (Boolean.TRUE.equals(skipHardGoalCheck)) {
                rebalanceBuilder.editSpec().withSkipHardGoalCheck(true).endSpec();
            }
            if (Boolean.TRUE.equals(rebalanceDisk)) {
                rebalanceBuilder.editSpec().withRebalanceDisk(true).endSpec();
            }
            if (concurrentPartition != null) {
                rebalanceBuilder.editSpec().withConcurrentPartitionMovementsPerBroker(concurrentPartition).endSpec();
            }
            if (concurrentIntraBroker != null) {
                rebalanceBuilder.editSpec().withConcurrentIntraBrokerPartitionMovements(concurrentIntraBroker).endSpec();
            }
            if (concurrentLeader != null) {
                rebalanceBuilder.editSpec().withConcurrentLeaderMovements(concurrentLeader).endSpec();
            }

            KafkaRebalance rebalance = rebalanceBuilder.build();

            kubernetesClient.resources(KafkaRebalance.class, KafkaRebalanceList.class)
                    .inNamespace(namespace)
                    .resource(rebalance)
                    .create();

            StringBuilder result = new StringBuilder();
            result.append("Created KafkaRebalance: ").append(namespace).append("/").append(name).append("\n");
            result.append("  Kafka Cluster: ").append(kafkaCluster).append("\n");
            result.append("  Mode: ").append(mode != null ? mode : "full").append("\n");
            if (brokers != null && !brokers.isEmpty()) {
                result.append("  Brokers: ").append(brokers).append("\n");
            }
            if (goals != null && !goals.isEmpty()) {
                result.append("  Goals: ").append(goals.size()).append(" custom goals\n");
            }
            result.append("\nCruise Control will generate an optimization proposal.\n");
            result.append("Use describe_rebalance to check the proposal and approve_rebalance to execute it.");

            return success(result.toString());
        } catch (Exception e) {
            return error("Error creating rebalance: " + e.getMessage());
        }
    }
}
