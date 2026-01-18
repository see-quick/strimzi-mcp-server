package io.seequick.mcp.tool.topic;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.seequick.mcp.tool.StrimziLabels;
import io.strimzi.api.kafka.model.common.Condition;
import io.strimzi.api.kafka.model.common.ConditionBuilder;
import io.strimzi.api.kafka.model.topic.KafkaTopic;
import io.strimzi.api.kafka.model.topic.KafkaTopicBuilder;
import io.strimzi.api.kafka.model.topic.KafkaTopicStatusBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class GetUnreadyTopicsToolTest {

    KubernetesClient client;

    private GetUnreadyTopicsTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetUnreadyTopicsTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("get_unready_topics");
    }

    @Test
    void executeShouldReportAllTopicsReadyWhenNoneUnready() {
        createTopicWithStatus("ready-topic", "kafka", "my-cluster", true, null);

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_unready_topics", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("All topics are in Ready state");
    }

    @Test
    void executeShouldListUnreadyTopics() {
        createTopicWithStatus("healthy-topic", "kafka", "my-cluster", true, null);
        createTopicWithStatus("failing-topic", "kafka", "my-cluster", false, "TopicOperatorError");

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_unready_topics", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 1 unready topic(s)");
        assertThat(content).contains("failing-topic");
        assertThat(content).doesNotContain("healthy-topic");
    }

    @Test
    void executeShouldListTopicsWithNoStatus() {
        // Topic without status is considered unready
        createTopicWithoutStatus("no-status-topic", "kafka", "my-cluster");

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_unready_topics", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 1 unready topic(s)");
        assertThat(content).contains("no-status-topic");
        assertThat(content).contains("No status available");
    }

    @Test
    void executeShouldFilterByKafkaCluster() {
        createTopicWithStatus("topic-a", "kafka", "cluster-a", false, null);
        createTopicWithStatus("topic-b", "kafka", "cluster-b", false, null);

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        args.put("kafkaCluster", "cluster-a");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_unready_topics", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("topic-a");
        assertThat(content).doesNotContain("topic-b");
    }

    @Test
    void executeShouldSearchAllNamespacesWhenNotSpecified() {
        createTopicWithStatus("topic-ns1", "ns1", "my-cluster", false, null);
        createTopicWithStatus("topic-ns2", "ns2", "my-cluster", false, null);

        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_unready_topics", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 unready topic(s)");
        assertThat(content).contains("topic-ns1");
        assertThat(content).contains("topic-ns2");
    }

    private void createTopicWithStatus(String name, String namespace, String cluster, boolean ready, String reason) {
        Condition condition = new ConditionBuilder()
                .withType("Ready")
                .withStatus(ready ? "True" : "False")
                .withReason(reason)
                .build();

        KafkaTopic topic = new KafkaTopicBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(StrimziLabels.CLUSTER, cluster)
                .endMetadata()
                .withNewSpec()
                    .withPartitions(1)
                    .withReplicas(1)
                .endSpec()
                .withStatus(new KafkaTopicStatusBuilder()
                        .withConditions(List.of(condition))
                        .build())
                .build();

        client.resources(KafkaTopic.class).inNamespace(namespace).resource(topic).create();
    }

    private void createTopicWithoutStatus(String name, String namespace, String cluster) {
        KafkaTopic topic = new KafkaTopicBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(StrimziLabels.CLUSTER, cluster)
                .endMetadata()
                .withNewSpec()
                    .withPartitions(1)
                    .withReplicas(1)
                .endSpec()
                .build();

        client.resources(KafkaTopic.class).inNamespace(namespace).resource(topic).create();
    }
}
