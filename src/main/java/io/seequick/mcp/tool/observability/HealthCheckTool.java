package io.seequick.mcp.tool.observability;

import io.fabric8.kubernetes.api.model.Pod;
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
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.List;

/**
 * Tool to perform a comprehensive health check of Strimzi resources.
 */
public class HealthCheckTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace to check. If not specified, checks all namespaces."
                    },
                    "kafkaCluster": {
                        "type": "string",
                        "description": "Optional: specific Kafka cluster to check"
                    }
                }
            }
            """;

    public HealthCheckTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "health_check";
    }

    @Override
    protected String getDescription() {
        return "Perform a comprehensive health check of Strimzi resources and report any issues";
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

            StringBuilder result = new StringBuilder();
            result.append("Strimzi Health Check Report\n");
            result.append("═".repeat(60)).append("\n\n");

            int totalIssues = 0;
            int warnings = 0;

            // Check Kafka clusters
            List<Kafka> kafkas;
            if (namespace != null) {
                kafkas = kubernetesClient.resources(Kafka.class, KafkaList.class)
                        .inNamespace(namespace)
                        .list()
                        .getItems();
            } else {
                kafkas = kubernetesClient.resources(Kafka.class, KafkaList.class)
                        .inAnyNamespace()
                        .list()
                        .getItems();
            }

            if (kafkaCluster != null) {
                kafkas = kafkas.stream()
                        .filter(k -> k.getMetadata().getName().equals(kafkaCluster))
                        .toList();
            }

            result.append("KAFKA CLUSTERS\n");
            result.append("─".repeat(40)).append("\n");

            if (kafkas.isEmpty()) {
                result.append("  No Kafka clusters found.\n");
            } else {
                for (Kafka kafka : kafkas) {
                    String ns = kafka.getMetadata().getNamespace();
                    String name = kafka.getMetadata().getName();
                    result.append("  ").append(ns).append("/").append(name).append(": ");

                    boolean isReady = false;
                    String message = "";

                    if (kafka.getStatus() != null && kafka.getStatus().getConditions() != null) {
                        for (var condition : kafka.getStatus().getConditions()) {
                            if ("Ready".equals(condition.getType())) {
                                isReady = "True".equals(condition.getStatus());
                                message = condition.getMessage() != null ? condition.getMessage() : "";
                                break;
                            }
                        }
                    }

                    if (isReady) {
                        result.append("✓ Ready\n");
                    } else {
                        result.append("✗ Not Ready");
                        if (!message.isEmpty()) {
                            result.append(" - ").append(message);
                        }
                        result.append("\n");
                        totalIssues++;
                    }

                    // Check broker pods
                    List<Pod> brokerPods = kubernetesClient.pods()
                            .inNamespace(ns)
                            .withLabel("strimzi.io/cluster", name)
                            .withLabel("strimzi.io/kind", "Kafka")
                            .list()
                            .getItems();

                    long runningBrokers = brokerPods.stream()
                            .filter(p -> "Running".equals(p.getStatus().getPhase()))
                            .count();
                    long notRunning = brokerPods.size() - runningBrokers;

                    result.append("    Brokers: ").append(runningBrokers).append("/")
                            .append(brokerPods.size()).append(" running");
                    if (notRunning > 0) {
                        result.append(" ⚠");
                        warnings++;
                    }
                    result.append("\n");
                }
            }
            result.append("\n");

            // Check Topics
            List<KafkaTopic> topics;
            if (namespace != null) {
                var topicsResource = kubernetesClient.resources(KafkaTopic.class, KafkaTopicList.class)
                        .inNamespace(namespace);
                if (kafkaCluster != null) {
                    topics = topicsResource.withLabel("strimzi.io/cluster", kafkaCluster).list().getItems();
                } else {
                    topics = topicsResource.list().getItems();
                }
            } else {
                topics = kubernetesClient.resources(KafkaTopic.class, KafkaTopicList.class)
                        .inAnyNamespace()
                        .list()
                        .getItems();
                if (kafkaCluster != null) {
                    topics = topics.stream()
                            .filter(t -> kafkaCluster.equals(t.getMetadata().getLabels().get("strimzi.io/cluster")))
                            .toList();
                }
            }

            long unreadyTopics = topics.stream()
                    .filter(t -> {
                        if (t.getStatus() == null || t.getStatus().getConditions() == null) return true;
                        return t.getStatus().getConditions().stream()
                                .noneMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
                    })
                    .count();

            result.append("TOPICS\n");
            result.append("─".repeat(40)).append("\n");
            result.append("  Total: ").append(topics.size()).append("\n");
            result.append("  Ready: ").append(topics.size() - unreadyTopics).append("\n");
            if (unreadyTopics > 0) {
                result.append("  Not Ready: ").append(unreadyTopics).append(" ⚠\n");
                warnings++;
            }
            result.append("\n");

            // Check Users
            List<KafkaUser> users;
            if (namespace != null) {
                users = kubernetesClient.resources(KafkaUser.class, KafkaUserList.class)
                        .inNamespace(namespace)
                        .list()
                        .getItems();
            } else {
                users = kubernetesClient.resources(KafkaUser.class, KafkaUserList.class)
                        .inAnyNamespace()
                        .list()
                        .getItems();
            }

            if (kafkaCluster != null) {
                users = users.stream()
                        .filter(u -> u.getMetadata().getLabels() != null &&
                                kafkaCluster.equals(u.getMetadata().getLabels().get("strimzi.io/cluster")))
                        .toList();
            }

            long unreadyUsers = users.stream()
                    .filter(u -> {
                        if (u.getStatus() == null || u.getStatus().getConditions() == null) return true;
                        return u.getStatus().getConditions().stream()
                                .noneMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
                    })
                    .count();

            result.append("USERS\n");
            result.append("─".repeat(40)).append("\n");
            result.append("  Total: ").append(users.size()).append("\n");
            result.append("  Ready: ").append(users.size() - unreadyUsers).append("\n");
            if (unreadyUsers > 0) {
                result.append("  Not Ready: ").append(unreadyUsers).append(" ⚠\n");
                warnings++;
            }
            result.append("\n");

            // Check Kafka Connect & Connectors
            List<KafkaConnect> connects;
            if (namespace != null) {
                connects = kubernetesClient.resources(KafkaConnect.class, KafkaConnectList.class)
                        .inNamespace(namespace)
                        .list()
                        .getItems();
            } else {
                connects = kubernetesClient.resources(KafkaConnect.class, KafkaConnectList.class)
                        .inAnyNamespace()
                        .list()
                        .getItems();
            }

            List<KafkaConnector> connectors;
            if (namespace != null) {
                connectors = kubernetesClient.resources(KafkaConnector.class, KafkaConnectorList.class)
                        .inNamespace(namespace)
                        .list()
                        .getItems();
            } else {
                connectors = kubernetesClient.resources(KafkaConnector.class, KafkaConnectorList.class)
                        .inAnyNamespace()
                        .list()
                        .getItems();
            }

            long failedConnectors = connectors.stream()
                    .filter(c -> {
                        if (c.getStatus() == null || c.getStatus().getConditions() == null) return true;
                        return c.getStatus().getConditions().stream()
                                .noneMatch(cond -> "Ready".equals(cond.getType()) && "True".equals(cond.getStatus()));
                    })
                    .count();

            result.append("KAFKA CONNECT\n");
            result.append("─".repeat(40)).append("\n");
            result.append("  Connect Clusters: ").append(connects.size()).append("\n");
            result.append("  Connectors: ").append(connectors.size()).append("\n");
            if (failedConnectors > 0) {
                result.append("  Failed/Not Ready: ").append(failedConnectors).append(" ⚠\n");
                warnings++;
            }
            result.append("\n");

            // Summary
            result.append("═".repeat(60)).append("\n");
            result.append("SUMMARY\n");
            if (totalIssues == 0 && warnings == 0) {
                result.append("✓ All resources are healthy!\n");
            } else {
                if (totalIssues > 0) {
                    result.append("✗ Critical issues: ").append(totalIssues).append("\n");
                }
                if (warnings > 0) {
                    result.append("⚠ Warnings: ").append(warnings).append("\n");
                }
                result.append("\nUse describe_* tools to investigate specific resources.\n");
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error performing health check: " + e.getMessage());
        }
    }
}
