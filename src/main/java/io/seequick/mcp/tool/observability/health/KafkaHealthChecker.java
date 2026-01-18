package io.seequick.mcp.tool.observability.health;

import io.fabric8.kubernetes.api.model.Pod;
import io.strimzi.api.kafka.model.kafka.Kafka;
import io.strimzi.api.kafka.model.kafka.KafkaList;
import io.seequick.mcp.tool.StrimziLabels;

import java.util.List;

/**
 * Health checker for Kafka clusters and broker pods.
 */
public class KafkaHealthChecker implements HealthChecker {

    @Override
    public String getSectionTitle() {
        return "KAFKA CLUSTERS";
    }

    @Override
    public void check(HealthCheckContext context, HealthCheckResult result) {
        result.startSection(getSectionTitle());

        List<Kafka> kafkas = listKafkas(context);

        if (kafkas.isEmpty()) {
            result.append("  No Kafka clusters found.\n");
        } else {
            for (Kafka kafka : kafkas) {
                checkKafkaCluster(context, kafka, result);
            }
        }

        result.newLine();
    }

    private List<Kafka> listKafkas(HealthCheckContext context) {
        List<Kafka> kafkas;

        if (context.hasNamespaceFilter()) {
            kafkas = context.getClient().resources(Kafka.class, KafkaList.class)
                    .inNamespace(context.getNamespace())
                    .list()
                    .getItems();
        } else {
            kafkas = context.getClient().resources(Kafka.class, KafkaList.class)
                    .inAnyNamespace()
                    .list()
                    .getItems();
        }

        if (context.hasClusterFilter()) {
            kafkas = kafkas.stream()
                    .filter(k -> k.getMetadata().getName().equals(context.getKafkaCluster()))
                    .toList();
        }

        return kafkas;
    }

    private void checkKafkaCluster(HealthCheckContext context, Kafka kafka, HealthCheckResult result) {
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
            result.append("\u2713 Ready\n");
        } else {
            result.append("\u2717 Not Ready");
            if (!message.isEmpty()) {
                result.append(" - ").append(message);
            }
            result.newLine();
            result.addIssue();
        }

        // Check broker pods
        checkBrokerPods(context, ns, name, result);
    }

    private void checkBrokerPods(HealthCheckContext context, String namespace, String clusterName, HealthCheckResult result) {
        List<Pod> brokerPods = context.getClient().pods()
                .inNamespace(namespace)
                .withLabel(StrimziLabels.CLUSTER, clusterName)
                .withLabel(StrimziLabels.KIND, "Kafka")
                .list()
                .getItems();

        long runningBrokers = brokerPods.stream()
                .filter(p -> "Running".equals(p.getStatus().getPhase()))
                .count();
        long notRunning = brokerPods.size() - runningBrokers;

        result.append("    Brokers: ").append(String.valueOf(runningBrokers)).append("/")
                .append(String.valueOf(brokerPods.size())).append(" running");
        if (notRunning > 0) {
            result.append(" \u26A0");
            result.addWarning();
        }
        result.newLine();
    }
}
