package io.seequick.mcp.tool.observability.health;

import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * Context for health check operations, containing shared state and configuration.
 */
public class HealthCheckContext {

    private final KubernetesClient client;
    private final String namespace;
    private final String kafkaCluster;

    public HealthCheckContext(KubernetesClient client, String namespace, String kafkaCluster) {
        this.client = client;
        this.namespace = namespace;
        this.kafkaCluster = kafkaCluster;
    }

    public KubernetesClient getClient() {
        return client;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getKafkaCluster() {
        return kafkaCluster;
    }

    /**
     * Checks if the namespace filter is set.
     */
    public boolean hasNamespaceFilter() {
        return namespace != null && !namespace.isEmpty();
    }

    /**
     * Checks if the Kafka cluster filter is set.
     */
    public boolean hasClusterFilter() {
        return kafkaCluster != null && !kafkaCluster.isEmpty();
    }
}
