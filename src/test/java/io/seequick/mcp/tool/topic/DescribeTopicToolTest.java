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
class DescribeTopicToolTest {

    KubernetesClient client;

    private DescribeTopicTool tool;

    @BeforeEach
    void setUp() {
        tool = new DescribeTopicTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("describe_topic");
    }

    @Test
    void getDescriptionShouldReturnCorrectDescription() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().description()).contains("detailed information");
    }

    @Test
    void executeShouldDescribeExistingTopic() {
        // Create a topic
        Map<String, Object> config = new HashMap<>();
        config.put("retention.ms", "86400000");

        KafkaTopic topic = new KafkaTopicBuilder()
                .withNewMetadata()
                    .withName("my-topic")
                    .withNamespace("kafka")
                    .addToLabels(StrimziLabels.CLUSTER, "production-cluster")
                .endMetadata()
                .withNewSpec()
                    .withPartitions(6)
                    .withReplicas(3)
                    .withConfig(config)
                .endSpec()
                .build();
        client.resources(KafkaTopic.class).inNamespace("kafka").resource(topic).create();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "my-topic");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_topic", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("KafkaTopic: kafka/my-topic");
        assertThat(content).contains("Kafka Cluster: production-cluster");
        assertThat(content).contains("Partitions: 6");
        assertThat(content).contains("Replicas: 3");
        assertThat(content).contains("retention.ms");
    }

    @Test
    void executeShouldDescribeTopicWithoutConfig() {
        KafkaTopic topic = new KafkaTopicBuilder()
                .withNewMetadata()
                    .withName("simple-topic")
                    .withNamespace("kafka")
                    .addToLabels(StrimziLabels.CLUSTER, "my-cluster")
                .endMetadata()
                .withNewSpec()
                    .withPartitions(1)
                    .withReplicas(1)
                .endSpec()
                .build();
        client.resources(KafkaTopic.class).inNamespace("kafka").resource(topic).create();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "simple-topic");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_topic", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("KafkaTopic: kafka/simple-topic");
        assertThat(content).contains("Partitions: 1");
    }

    @Test
    void executeShouldFailWhenTopicDoesNotExist() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "non-existent");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_topic", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }
}
