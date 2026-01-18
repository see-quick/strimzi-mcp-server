package io.seequick.mcp.tool.topic;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.seequick.mcp.tool.StrimziLabels;
import io.strimzi.api.kafka.model.topic.KafkaTopic;
import io.strimzi.api.kafka.model.topic.KafkaTopicBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class CompareTopicConfigToolTest {

    KubernetesClient client;

    private CompareTopicConfigTool tool;

    @BeforeEach
    void setUp() {
        tool = new CompareTopicConfigTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("compare_topic_config");
    }

    @Test
    void executeShouldCompareAgainstDefaults() {
        Map<String, Object> config = new HashMap<>();
        config.put("retention.ms", "86400000");
        createTopicWithConfig("my-topic", "kafka", "my-cluster", 3, 3, config);

        Map<String, Object> args = new HashMap<>();
        args.put("topic1", "my-topic");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("compare_topic_config", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Topic Configuration vs Kafka Defaults");
        assertThat(content).contains("retention.ms");
        assertThat(content).contains("86400000");
    }

    @Test
    void executeShouldCompareTwoTopics() {
        Map<String, Object> config1 = new HashMap<>();
        config1.put("retention.ms", "86400000");
        createTopicWithConfig("topic-1", "kafka", "my-cluster", 3, 3, config1);

        Map<String, Object> config2 = new HashMap<>();
        config2.put("retention.ms", "172800000");
        createTopicWithConfig("topic-2", "kafka", "my-cluster", 6, 3, config2);

        Map<String, Object> args = new HashMap<>();
        args.put("topic1", "topic-1");
        args.put("topic2", "topic-2");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("compare_topic_config", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Comparing Topics");
        assertThat(content).contains("topic-1");
        assertThat(content).contains("topic-2");
    }

    @Test
    void executeShouldFailWhenTopic1NotFound() {
        Map<String, Object> args = new HashMap<>();
        args.put("topic1", "non-existent");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("compare_topic_config", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }

    @Test
    void executeShouldFailWhenTopic2NotFound() {
        createTopic("topic-1", "kafka", "my-cluster", 3, 3);

        Map<String, Object> args = new HashMap<>();
        args.put("topic1", "topic-1");
        args.put("topic2", "non-existent");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("compare_topic_config", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }

    @Test
    void executeShouldShowDefaultsMessageWhenNoConfig() {
        createTopic("default-topic", "kafka", "my-cluster", 3, 3);

        Map<String, Object> args = new HashMap<>();
        args.put("topic1", "default-topic");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("compare_topic_config", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("default");
    }

    private void createTopic(String name, String namespace, String cluster, int partitions, int replicas) {
        createTopicWithConfig(name, namespace, cluster, partitions, replicas, null);
    }

    private void createTopicWithConfig(String name, String namespace, String cluster, int partitions, int replicas, Map<String, Object> config) {
        var builder = new KafkaTopicBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(StrimziLabels.CLUSTER, cluster)
                .endMetadata()
                .withNewSpec()
                    .withPartitions(partitions)
                    .withReplicas(replicas);

        if (config != null) {
            builder.withConfig(config);
        }

        KafkaTopic topic = builder.endSpec().build();
        client.resources(KafkaTopic.class).inNamespace(namespace).resource(topic).create();
    }
}