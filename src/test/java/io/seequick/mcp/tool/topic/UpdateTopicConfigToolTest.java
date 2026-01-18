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
class UpdateTopicConfigToolTest {

    KubernetesClient client;

    private UpdateTopicConfigTool tool;

    @BeforeEach
    void setUp() {
        tool = new UpdateTopicConfigTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("update_topic_config");
    }

    @Test
    void executeShouldIncreasePartitions() {
        createTopic("my-topic", "kafka", 3, 1, null);

        Map<String, Object> args = new HashMap<>();
        args.put("name", "my-topic");
        args.put("namespace", "kafka");
        args.put("partitions", 6);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("update_topic_config", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Updated KafkaTopic");
        assertThat(content).contains("3 -> 6");

        KafkaTopic updated = client.resources(KafkaTopic.class)
                .inNamespace("kafka").withName("my-topic").get();
        assertThat(updated.getSpec().getPartitions()).isEqualTo(6);
    }

    @Test
    void executeShouldFailWhenDecreasingPartitions() {
        createTopic("my-topic", "kafka", 6, 1, null);

        Map<String, Object> args = new HashMap<>();
        args.put("name", "my-topic");
        args.put("namespace", "kafka");
        args.put("partitions", 3);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("update_topic_config", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Cannot decrease partitions");
    }

    @Test
    void executeShouldUpdateConfig() {
        createTopic("my-topic", "kafka", 1, 1, null);

        Map<String, Object> config = new HashMap<>();
        config.put("retention.ms", "172800000");
        config.put("cleanup.policy", "compact");

        Map<String, Object> args = new HashMap<>();
        args.put("name", "my-topic");
        args.put("namespace", "kafka");
        args.put("config", config);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("update_topic_config", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Config updates");

        KafkaTopic updated = client.resources(KafkaTopic.class)
                .inNamespace("kafka").withName("my-topic").get();
        assertThat(updated.getSpec().getConfig()).containsKey("retention.ms");
        assertThat(updated.getSpec().getConfig()).containsKey("cleanup.policy");
    }

    @Test
    void executeShouldMergeWithExistingConfig() {
        Map<String, Object> existingConfig = new HashMap<>();
        existingConfig.put("retention.ms", "86400000");
        createTopic("my-topic", "kafka", 1, 1, existingConfig);

        Map<String, Object> newConfig = new HashMap<>();
        newConfig.put("cleanup.policy", "compact");

        Map<String, Object> args = new HashMap<>();
        args.put("name", "my-topic");
        args.put("namespace", "kafka");
        args.put("config", newConfig);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("update_topic_config", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();

        KafkaTopic updated = client.resources(KafkaTopic.class)
                .inNamespace("kafka").withName("my-topic").get();
        // Both old and new config should be present
        assertThat(updated.getSpec().getConfig()).containsKey("retention.ms");
        assertThat(updated.getSpec().getConfig()).containsKey("cleanup.policy");
    }

    @Test
    void executeShouldFailWhenTopicDoesNotExist() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "non-existent");
        args.put("namespace", "kafka");
        args.put("partitions", 3);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("update_topic_config", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }

    @Test
    void executeShouldFailWhenNoUpdatesProvided() {
        createTopic("my-topic", "kafka", 1, 1, null);

        Map<String, Object> args = new HashMap<>();
        args.put("name", "my-topic");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("update_topic_config", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("No updates specified");
    }

    private void createTopic(String name, String namespace, int partitions, int replicas, Map<String, Object> config) {
        var builder = new KafkaTopicBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(StrimziLabels.CLUSTER, "my-cluster")
                .endMetadata()
                .withNewSpec()
                    .withPartitions(partitions)
                    .withReplicas(replicas)
                .endSpec();

        if (config != null) {
            builder.editSpec().withConfig(config).endSpec();
        }

        client.resources(KafkaTopic.class).inNamespace(namespace).resource(builder.build()).create();
    }
}
