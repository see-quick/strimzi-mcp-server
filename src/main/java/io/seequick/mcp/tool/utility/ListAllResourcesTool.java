package io.seequick.mcp.tool.utility;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.kafka.Kafka;
import io.strimzi.api.kafka.model.kafka.KafkaList;
import io.strimzi.api.kafka.model.topic.KafkaTopic;
import io.strimzi.api.kafka.model.topic.KafkaTopicList;
import io.strimzi.api.kafka.model.user.KafkaUser;
import io.strimzi.api.kafka.model.user.KafkaUserList;
import io.strimzi.api.kafka.model.connect.KafkaConnect;
import io.strimzi.api.kafka.model.connect.KafkaConnectList;
import io.strimzi.api.kafka.model.connector.KafkaConnector;
import io.strimzi.api.kafka.model.connector.KafkaConnectorList;
import io.strimzi.api.kafka.model.nodepool.KafkaNodePool;
import io.strimzi.api.kafka.model.nodepool.KafkaNodePoolList;
import io.strimzi.api.kafka.model.mirrormaker2.KafkaMirrorMaker2;
import io.strimzi.api.kafka.model.mirrormaker2.KafkaMirrorMaker2List;
import io.strimzi.api.kafka.model.bridge.KafkaBridge;
import io.strimzi.api.kafka.model.bridge.KafkaBridgeList;
import io.strimzi.api.kafka.model.rebalance.KafkaRebalance;
import io.strimzi.api.kafka.model.rebalance.KafkaRebalanceList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.List;

/**
 * Tool to list all Strimzi resources in the cluster.
 */
public class ListAllResourcesTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace to list resources from. If not specified, lists from all namespaces."
                    }
                }
            }
            """;

    public ListAllResourcesTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "list_all_resources";
    }

    @Override
    protected String getDescription() {
        return "List all Strimzi resources in the cluster (Kafka, Topics, Users, Connect, Connectors, etc.)";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    protected CallToolResult execute(McpSchema.CallToolRequest args) {
        try {
            String namespace = getStringArg(args, "namespace");

            StringBuilder result = new StringBuilder();
            result.append("Strimzi Resources Summary\n");
            result.append("═".repeat(60)).append("\n");
            if (namespace != null) {
                result.append("Namespace: ").append(namespace).append("\n");
            } else {
                result.append("Scope: All namespaces\n");
            }
            result.append("\n");

            int totalResources = 0;

            // Kafka Clusters
            List<Kafka> kafkas = listKafkas(namespace);
            result.append("KAFKA CLUSTERS (").append(kafkas.size()).append(")\n");
            result.append("─".repeat(40)).append("\n");
            for (Kafka kafka : kafkas) {
                result.append("  ").append(kafka.getMetadata().getNamespace())
                        .append("/").append(kafka.getMetadata().getName());
                if (kafka.getStatus() != null && kafka.getStatus().getConditions() != null) {
                    boolean ready = kafka.getStatus().getConditions().stream()
                            .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
                    result.append(ready ? " ✓" : " ✗");
                }
                result.append("\n");
            }
            totalResources += kafkas.size();
            result.append("\n");

            // Node Pools
            List<KafkaNodePool> nodePools = listNodePools(namespace);
            if (!nodePools.isEmpty()) {
                result.append("NODE POOLS (").append(nodePools.size()).append(")\n");
                result.append("─".repeat(40)).append("\n");
                for (KafkaNodePool np : nodePools) {
                    result.append("  ").append(np.getMetadata().getNamespace())
                            .append("/").append(np.getMetadata().getName());
                    if (np.getSpec() != null) {
                        result.append(" (").append(np.getSpec().getReplicas()).append(" replicas)");
                    }
                    result.append("\n");
                }
                totalResources += nodePools.size();
                result.append("\n");
            }

            // Topics
            List<KafkaTopic> topics = listTopics(namespace);
            result.append("TOPICS (").append(topics.size()).append(")\n");
            result.append("─".repeat(40)).append("\n");
            if (topics.size() <= 10) {
                for (KafkaTopic topic : topics) {
                    result.append("  ").append(topic.getMetadata().getNamespace())
                            .append("/").append(topic.getMetadata().getName()).append("\n");
                }
            } else {
                result.append("  (").append(topics.size()).append(" topics - use list_topics for details)\n");
            }
            totalResources += topics.size();
            result.append("\n");

            // Users
            List<KafkaUser> users = listUsers(namespace);
            result.append("USERS (").append(users.size()).append(")\n");
            result.append("─".repeat(40)).append("\n");
            if (users.size() <= 10) {
                for (KafkaUser user : users) {
                    result.append("  ").append(user.getMetadata().getNamespace())
                            .append("/").append(user.getMetadata().getName()).append("\n");
                }
            } else {
                result.append("  (").append(users.size()).append(" users - use list_users for details)\n");
            }
            totalResources += users.size();
            result.append("\n");

            // Kafka Connect
            List<KafkaConnect> connects = listConnects(namespace);
            if (!connects.isEmpty()) {
                result.append("KAFKA CONNECT (").append(connects.size()).append(")\n");
                result.append("─".repeat(40)).append("\n");
                for (KafkaConnect connect : connects) {
                    result.append("  ").append(connect.getMetadata().getNamespace())
                            .append("/").append(connect.getMetadata().getName()).append("\n");
                }
                totalResources += connects.size();
                result.append("\n");
            }

            // Connectors
            List<KafkaConnector> connectors = listConnectors(namespace);
            if (!connectors.isEmpty()) {
                result.append("CONNECTORS (").append(connectors.size()).append(")\n");
                result.append("─".repeat(40)).append("\n");
                for (KafkaConnector connector : connectors) {
                    result.append("  ").append(connector.getMetadata().getNamespace())
                            .append("/").append(connector.getMetadata().getName()).append("\n");
                }
                totalResources += connectors.size();
                result.append("\n");
            }

            // MirrorMaker2
            List<KafkaMirrorMaker2> mm2s = listMM2s(namespace);
            if (!mm2s.isEmpty()) {
                result.append("MIRRORMAKER2 (").append(mm2s.size()).append(")\n");
                result.append("─".repeat(40)).append("\n");
                for (KafkaMirrorMaker2 mm2 : mm2s) {
                    result.append("  ").append(mm2.getMetadata().getNamespace())
                            .append("/").append(mm2.getMetadata().getName()).append("\n");
                }
                totalResources += mm2s.size();
                result.append("\n");
            }

            // Bridges
            List<KafkaBridge> bridges = listBridges(namespace);
            if (!bridges.isEmpty()) {
                result.append("BRIDGES (").append(bridges.size()).append(")\n");
                result.append("─".repeat(40)).append("\n");
                for (KafkaBridge bridge : bridges) {
                    result.append("  ").append(bridge.getMetadata().getNamespace())
                            .append("/").append(bridge.getMetadata().getName()).append("\n");
                }
                totalResources += bridges.size();
                result.append("\n");
            }

            // Rebalances
            List<KafkaRebalance> rebalances = listRebalances(namespace);
            if (!rebalances.isEmpty()) {
                result.append("REBALANCES (").append(rebalances.size()).append(")\n");
                result.append("─".repeat(40)).append("\n");
                for (KafkaRebalance rebalance : rebalances) {
                    result.append("  ").append(rebalance.getMetadata().getNamespace())
                            .append("/").append(rebalance.getMetadata().getName());
                    if (rebalance.getStatus() != null && rebalance.getStatus().getConditions() != null) {
                        String state = rebalance.getStatus().getConditions().stream()
                                .filter(c -> "True".equals(c.getStatus()))
                                .map(c -> c.getType())
                                .findFirst()
                                .orElse("Unknown");
                        result.append(" (").append(state).append(")");
                    }
                    result.append("\n");
                }
                totalResources += rebalances.size();
                result.append("\n");
            }

            // Summary
            result.append("═".repeat(60)).append("\n");
            result.append("TOTAL: ").append(totalResources).append(" Strimzi resources\n");

            return success(result.toString());
        } catch (Exception e) {
            return error("Error listing resources: " + e.getMessage());
        }
    }

    private List<Kafka> listKafkas(String namespace) {
        if (namespace != null) {
            return kubernetesClient.resources(Kafka.class, KafkaList.class)
                    .inNamespace(namespace).list().getItems();
        }
        return kubernetesClient.resources(Kafka.class, KafkaList.class)
                .inAnyNamespace().list().getItems();
    }

    private List<KafkaNodePool> listNodePools(String namespace) {
        if (namespace != null) {
            return kubernetesClient.resources(KafkaNodePool.class, KafkaNodePoolList.class)
                    .inNamespace(namespace).list().getItems();
        }
        return kubernetesClient.resources(KafkaNodePool.class, KafkaNodePoolList.class)
                .inAnyNamespace().list().getItems();
    }

    private List<KafkaTopic> listTopics(String namespace) {
        if (namespace != null) {
            return kubernetesClient.resources(KafkaTopic.class, KafkaTopicList.class)
                    .inNamespace(namespace).list().getItems();
        }
        return kubernetesClient.resources(KafkaTopic.class, KafkaTopicList.class)
                .inAnyNamespace().list().getItems();
    }

    private List<KafkaUser> listUsers(String namespace) {
        if (namespace != null) {
            return kubernetesClient.resources(KafkaUser.class, KafkaUserList.class)
                    .inNamespace(namespace).list().getItems();
        }
        return kubernetesClient.resources(KafkaUser.class, KafkaUserList.class)
                .inAnyNamespace().list().getItems();
    }

    private List<KafkaConnect> listConnects(String namespace) {
        if (namespace != null) {
            return kubernetesClient.resources(KafkaConnect.class, KafkaConnectList.class)
                    .inNamespace(namespace).list().getItems();
        }
        return kubernetesClient.resources(KafkaConnect.class, KafkaConnectList.class)
                .inAnyNamespace().list().getItems();
    }

    private List<KafkaConnector> listConnectors(String namespace) {
        if (namespace != null) {
            return kubernetesClient.resources(KafkaConnector.class, KafkaConnectorList.class)
                    .inNamespace(namespace).list().getItems();
        }
        return kubernetesClient.resources(KafkaConnector.class, KafkaConnectorList.class)
                .inAnyNamespace().list().getItems();
    }

    private List<KafkaMirrorMaker2> listMM2s(String namespace) {
        if (namespace != null) {
            return kubernetesClient.resources(KafkaMirrorMaker2.class, KafkaMirrorMaker2List.class)
                    .inNamespace(namespace).list().getItems();
        }
        return kubernetesClient.resources(KafkaMirrorMaker2.class, KafkaMirrorMaker2List.class)
                .inAnyNamespace().list().getItems();
    }

    private List<KafkaBridge> listBridges(String namespace) {
        if (namespace != null) {
            return kubernetesClient.resources(KafkaBridge.class, KafkaBridgeList.class)
                    .inNamespace(namespace).list().getItems();
        }
        return kubernetesClient.resources(KafkaBridge.class, KafkaBridgeList.class)
                .inAnyNamespace().list().getItems();
    }

    private List<KafkaRebalance> listRebalances(String namespace) {
        if (namespace != null) {
            return kubernetesClient.resources(KafkaRebalance.class, KafkaRebalanceList.class)
                    .inNamespace(namespace).list().getItems();
        }
        return kubernetesClient.resources(KafkaRebalance.class, KafkaRebalanceList.class)
                .inAnyNamespace().list().getItems();
    }
}
