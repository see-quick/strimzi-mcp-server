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
class CreateTopicToolTest {

    KubernetesClient client;

    private CreateTopicTool tool;

    @BeforeEach
    void setUp() {
        tool = new CreateTopicTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("create_topic");
    }

    @Test
    void getDescriptionShouldReturnCorrectDescription() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().description()).contains("Create a new KafkaTopic");
    }

    @Test
    void executeShouldCreateTopicWithRequiredFields() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "my-topic");
        args.put("namespace", "kafka");
        args.put("kafkaCluster", "my-cluster");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("create_topic", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Created KafkaTopic: kafka/my-topic");
        assertThat(content).contains("Kafka Cluster: my-cluster");
        assertThat(content).contains("Partitions: 1");
        assertThat(content).contains("Replicas: 1");

        // Verify topic was actually created
        KafkaTopic created = client.resources(KafkaTopic.class)
                .inNamespace("kafka").withName("my-topic").get();
        assertThat(created).isNotNull();
        assertThat(created.getMetadata().getLabels().get(StrimziLabels.CLUSTER)).isEqualTo("my-cluster");
    }

    @Test
    void executeShouldCreateTopicWithCustomPartitionsAndReplicas() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "partitioned-topic");
        args.put("namespace", "kafka");
        args.put("kafkaCluster", "my-cluster");
        args.put("partitions", 12);
        args.put("replicas", 3);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("create_topic", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Partitions: 12");
        assertThat(content).contains("Replicas: 3");

        KafkaTopic created = client.resources(KafkaTopic.class)
                .inNamespace("kafka").withName("partitioned-topic").get();
        assertThat(created.getSpec().getPartitions()).isEqualTo(12);
        assertThat(created.getSpec().getReplicas()).isEqualTo(3);
    }

    @Test
    void executeShouldCreateTopicWithConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("retention.ms", "86400000");
        config.put("cleanup.policy", "compact");

        Map<String, Object> args = new HashMap<>();
        args.put("name", "configured-topic");
        args.put("namespace", "kafka");
        args.put("kafkaCluster", "my-cluster");
        args.put("config", config);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("create_topic", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Config:");

        KafkaTopic created = client.resources(KafkaTopic.class)
                .inNamespace("kafka").withName("configured-topic").get();
        assertThat(created.getSpec().getConfig()).containsKey("retention.ms");
        assertThat(created.getSpec().getConfig()).containsKey("cleanup.policy");
    }

    @Test
    void executeShouldFailWhenTopicAlreadyExists() {
        // Create existing topic
        KafkaTopic existing = new KafkaTopicBuilder()
                .withNewMetadata()
                    .withName("existing-topic")
                    .withNamespace("kafka")
                    .addToLabels(StrimziLabels.CLUSTER, "my-cluster")
                .endMetadata()
                .withNewSpec()
                    .withPartitions(1)
                    .withReplicas(1)
                .endSpec()
                .build();
        client.resources(KafkaTopic.class).inNamespace("kafka").resource(existing).create();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "existing-topic");
        args.put("namespace", "kafka");
        args.put("kafkaCluster", "my-cluster");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("create_topic", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("already exists");
    }
}
