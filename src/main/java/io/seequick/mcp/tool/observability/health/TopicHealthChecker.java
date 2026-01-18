package io.seequick.mcp.tool.observability.health;

import io.strimzi.api.kafka.model.topic.KafkaTopic;
import io.strimzi.api.kafka.model.topic.KafkaTopicList;
import io.seequick.mcp.tool.StrimziLabels;

import java.util.List;

/**
 * Health checker for Kafka topics.
 */
public class TopicHealthChecker implements HealthChecker {

    @Override
    public String getSectionTitle() {
        return "TOPICS";
    }

    @Override
    public void check(HealthCheckContext context, HealthCheckResult result) {
        result.startSection(getSectionTitle());

        List<KafkaTopic> topics = listTopics(context);

        long unreadyTopics = topics.stream()
                .filter(this::isUnready)
                .count();

        result.append("  Total: ").append(String.valueOf(topics.size())).newLine();
        result.append("  Ready: ").append(String.valueOf(topics.size() - unreadyTopics)).newLine();

        if (unreadyTopics > 0) {
            result.append("  Not Ready: ").append(String.valueOf(unreadyTopics)).append(" \u26A0\n");
            result.addWarning();
        }

        result.newLine();
    }

    private List<KafkaTopic> listTopics(HealthCheckContext context) {
        List<KafkaTopic> topics;

        if (context.hasNamespaceFilter()) {
            var topicsResource = context.getClient().resources(KafkaTopic.class, KafkaTopicList.class)
                    .inNamespace(context.getNamespace());
            if (context.hasClusterFilter()) {
                topics = topicsResource.withLabel(StrimziLabels.CLUSTER, context.getKafkaCluster())
                        .list().getItems();
            } else {
                topics = topicsResource.list().getItems();
            }
        } else {
            topics = context.getClient().resources(KafkaTopic.class, KafkaTopicList.class)
                    .inAnyNamespace()
                    .list()
                    .getItems();
            if (context.hasClusterFilter()) {
                topics = topics.stream()
                        .filter(t -> t.getMetadata().getLabels() != null &&
                                context.getKafkaCluster().equals(t.getMetadata().getLabels().get(StrimziLabels.CLUSTER)))
                        .toList();
            }
        }

        return topics;
    }

    private boolean isUnready(KafkaTopic topic) {
        if (topic.getStatus() == null || topic.getStatus().getConditions() == null) {
            return true;
        }
        return topic.getStatus().getConditions().stream()
                .noneMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
    }
}
