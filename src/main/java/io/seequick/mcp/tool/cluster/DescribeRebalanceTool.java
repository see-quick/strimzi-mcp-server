package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.strimzi.api.kafka.model.rebalance.KafkaRebalance;
import io.strimzi.api.kafka.model.rebalance.KafkaRebalanceList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.Map;

/**
 * Tool to get detailed information about a KafkaRebalance including optimization proposal.
 */
public class DescribeRebalanceTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaRebalance resource"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the rebalance"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    public DescribeRebalanceTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "describe_rebalance";
    }

    @Override
    protected String getDescription() {
        return "Get detailed information about a KafkaRebalance including optimization proposal and progress";
    }

    @Override
    protected String getInputSchema() {
        return SCHEMA;
    }

    @Override
    protected CallToolResult execute(Map<String, Object> args) {
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

            StringBuilder result = new StringBuilder();
            result.append("KafkaRebalance: ").append(namespace).append("/").append(name).append("\n\n");

            // Cluster label
            var labels = rebalance.getMetadata().getLabels();
            if (labels != null && labels.containsKey("strimzi.io/cluster")) {
                result.append("Kafka Cluster: ").append(labels.get("strimzi.io/cluster")).append("\n");
            }

            // Annotations (for approve/refresh actions)
            var annotations = rebalance.getMetadata().getAnnotations();
            if (annotations != null && annotations.containsKey("strimzi.io/rebalance")) {
                result.append("Rebalance Annotation: ").append(annotations.get("strimzi.io/rebalance")).append("\n");
            }

            var spec = rebalance.getSpec();
            if (spec != null) {
                result.append("\nSpec:\n");
                if (spec.getMode() != null) {
                    result.append("  Mode: ").append(spec.getMode()).append("\n");
                }
                if (spec.getBrokers() != null) {
                    result.append("  Brokers: ").append(spec.getBrokers()).append("\n");
                }
                if (spec.getGoals() != null && !spec.getGoals().isEmpty()) {
                    result.append("  Goals:\n");
                    for (String goal : spec.getGoals()) {
                        result.append("    - ").append(goal).append("\n");
                    }
                }
                if (spec.getConcurrentPartitionMovementsPerBroker() > 0) {
                    result.append("  Concurrent Partition Movements: ").append(spec.getConcurrentPartitionMovementsPerBroker()).append("\n");
                }
                if (spec.getConcurrentIntraBrokerPartitionMovements() > 0) {
                    result.append("  Concurrent Intra-Broker Movements: ").append(spec.getConcurrentIntraBrokerPartitionMovements()).append("\n");
                }
                if (spec.getConcurrentLeaderMovements() > 0) {
                    result.append("  Concurrent Leader Movements: ").append(spec.getConcurrentLeaderMovements()).append("\n");
                }
                if (spec.getReplicationThrottle() > 0) {
                    result.append("  Replication Throttle: ").append(spec.getReplicationThrottle()).append(" bytes/sec\n");
                }
            }

            // Status
            var status = rebalance.getStatus();
            if (status != null) {
                result.append("\nStatus:\n");

                // Current state from conditions
                if (status.getConditions() != null) {
                    result.append("  State: ");
                    for (var condition : status.getConditions()) {
                        if ("True".equals(condition.getStatus())) {
                            result.append(condition.getType());
                            if (condition.getReason() != null) {
                                result.append(" (").append(condition.getReason()).append(")");
                            }
                            break;
                        }
                    }
                    result.append("\n");

                    result.append("\n  Conditions:\n");
                    for (var condition : status.getConditions()) {
                        result.append("    - ").append(condition.getType())
                                .append(": ").append(condition.getStatus());
                        if (condition.getReason() != null) {
                            result.append(" (").append(condition.getReason()).append(")");
                        }
                        if (condition.getMessage() != null && !condition.getMessage().isEmpty()) {
                            result.append("\n      Message: ").append(condition.getMessage());
                        }
                        result.append("\n");
                    }
                }

                if (status.getSessionId() != null) {
                    result.append("\n  Session ID: ").append(status.getSessionId()).append("\n");
                }

                // Optimization result
                if (status.getOptimizationResult() != null && !status.getOptimizationResult().isEmpty()) {
                    result.append("\n  Optimization Proposal:\n");
                    var opt = status.getOptimizationResult();

                    if (opt.containsKey("numIntraBrokerReplicaMovements")) {
                        result.append("    Intra-Broker Replica Movements: ").append(opt.get("numIntraBrokerReplicaMovements")).append("\n");
                    }
                    if (opt.containsKey("numReplicaMovements")) {
                        result.append("    Replica Movements: ").append(opt.get("numReplicaMovements")).append("\n");
                    }
                    if (opt.containsKey("numLeaderMovements")) {
                        result.append("    Leader Movements: ").append(opt.get("numLeaderMovements")).append("\n");
                    }
                    if (opt.containsKey("dataToMoveMB")) {
                        result.append("    Data to Move: ").append(opt.get("dataToMoveMB")).append(" MB\n");
                    }
                    if (opt.containsKey("excludedTopics")) {
                        result.append("    Excluded Topics: ").append(opt.get("excludedTopics")).append("\n");
                    }
                    if (opt.containsKey("excludedBrokersForReplicaMove")) {
                        result.append("    Excluded Brokers: ").append(opt.get("excludedBrokersForReplicaMove")).append("\n");
                    }
                    if (opt.containsKey("monitoredPartitionsPercentage")) {
                        result.append("    Monitored Partitions: ").append(opt.get("monitoredPartitionsPercentage")).append("%\n");
                    }
                    if (opt.containsKey("provisionStatus")) {
                        result.append("    Provision Status: ").append(opt.get("provisionStatus")).append("\n");
                    }

                    // Before/After stats
                    if (opt.containsKey("beforeLoadConfigMap") || opt.containsKey("afterLoadConfigMap")) {
                        result.append("\n    Load Statistics:\n");
                        if (opt.containsKey("beforeLoadConfigMap")) {
                            result.append("      Before: ").append(opt.get("beforeLoadConfigMap")).append("\n");
                        }
                        if (opt.containsKey("afterLoadConfigMap")) {
                            result.append("      After: ").append(opt.get("afterLoadConfigMap")).append("\n");
                        }
                    }
                }

                result.append("\n  Observed Generation: ").append(status.getObservedGeneration()).append("\n");
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error describing rebalance: " + e.getMessage());
        }
    }
}
