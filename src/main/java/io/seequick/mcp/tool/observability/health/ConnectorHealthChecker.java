package io.seequick.mcp.tool.observability.health;

import io.strimzi.api.kafka.model.connect.KafkaConnect;
import io.strimzi.api.kafka.model.connect.KafkaConnectList;
import io.strimzi.api.kafka.model.connector.KafkaConnector;
import io.strimzi.api.kafka.model.connector.KafkaConnectorList;

import java.util.List;

/**
 * Health checker for Kafka Connect clusters and connectors.
 */
public class ConnectorHealthChecker implements HealthChecker {

    @Override
    public String getSectionTitle() {
        return "KAFKA CONNECT";
    }

    @Override
    public void check(HealthCheckContext context, HealthCheckResult result) {
        result.startSection(getSectionTitle());

        List<KafkaConnect> connects = listConnects(context);
        List<KafkaConnector> connectors = listConnectors(context);

        long failedConnectors = connectors.stream()
                .filter(this::isUnready)
                .count();

        result.append("  Connect Clusters: ").append(String.valueOf(connects.size())).newLine();
        result.append("  Connectors: ").append(String.valueOf(connectors.size())).newLine();

        if (failedConnectors > 0) {
            result.append("  Failed/Not Ready: ").append(String.valueOf(failedConnectors)).append(" \u26A0\n");
            result.addWarning();
        }

        result.newLine();
    }

    private List<KafkaConnect> listConnects(HealthCheckContext context) {
        if (context.hasNamespaceFilter()) {
            return context.getClient().resources(KafkaConnect.class, KafkaConnectList.class)
                    .inNamespace(context.getNamespace())
                    .list()
                    .getItems();
        } else {
            return context.getClient().resources(KafkaConnect.class, KafkaConnectList.class)
                    .inAnyNamespace()
                    .list()
                    .getItems();
        }
    }

    private List<KafkaConnector> listConnectors(HealthCheckContext context) {
        if (context.hasNamespaceFilter()) {
            return context.getClient().resources(KafkaConnector.class, KafkaConnectorList.class)
                    .inNamespace(context.getNamespace())
                    .list()
                    .getItems();
        } else {
            return context.getClient().resources(KafkaConnector.class, KafkaConnectorList.class)
                    .inAnyNamespace()
                    .list()
                    .getItems();
        }
    }

    private boolean isUnready(KafkaConnector connector) {
        if (connector.getStatus() == null || connector.getStatus().getConditions() == null) {
            return true;
        }
        return connector.getStatus().getConditions().stream()
                .noneMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
    }
}
