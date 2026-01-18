package io.seequick.mcp.tool.observability.health;

import io.strimzi.api.kafka.model.user.KafkaUser;
import io.strimzi.api.kafka.model.user.KafkaUserList;
import io.seequick.mcp.tool.StrimziLabels;

import java.util.List;

/**
 * Health checker for Kafka users.
 */
public class UserHealthChecker implements HealthChecker {

    @Override
    public String getSectionTitle() {
        return "USERS";
    }

    @Override
    public void check(HealthCheckContext context, HealthCheckResult result) {
        result.startSection(getSectionTitle());

        List<KafkaUser> users = listUsers(context);

        long unreadyUsers = users.stream()
                .filter(this::isUnready)
                .count();

        result.append("  Total: ").append(String.valueOf(users.size())).newLine();
        result.append("  Ready: ").append(String.valueOf(users.size() - unreadyUsers)).newLine();

        if (unreadyUsers > 0) {
            result.append("  Not Ready: ").append(String.valueOf(unreadyUsers)).append(" \u26A0\n");
            result.addWarning();
        }

        result.newLine();
    }

    private List<KafkaUser> listUsers(HealthCheckContext context) {
        List<KafkaUser> users;

        if (context.hasNamespaceFilter()) {
            users = context.getClient().resources(KafkaUser.class, KafkaUserList.class)
                    .inNamespace(context.getNamespace())
                    .list()
                    .getItems();
        } else {
            users = context.getClient().resources(KafkaUser.class, KafkaUserList.class)
                    .inAnyNamespace()
                    .list()
                    .getItems();
        }

        if (context.hasClusterFilter()) {
            users = users.stream()
                    .filter(u -> u.getMetadata().getLabels() != null &&
                            context.getKafkaCluster().equals(u.getMetadata().getLabels().get(StrimziLabels.CLUSTER)))
                    .toList();
        }

        return users;
    }

    private boolean isUnready(KafkaUser user) {
        if (user.getStatus() == null || user.getStatus().getConditions() == null) {
            return true;
        }
        return user.getStatus().getConditions().stream()
                .noneMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
    }
}
