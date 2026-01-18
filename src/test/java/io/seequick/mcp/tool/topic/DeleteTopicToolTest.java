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
class DeleteTopicToolTest {

    KubernetesClient client;

    private DeleteTopicTool tool;

    @BeforeEach
    void setUp() {
        tool = new DeleteTopicTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("delete_topic");
    }

    @Test
    void getDescriptionShouldReturnCorrectDescription() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().description()).contains("Delete a KafkaTopic");
    }

    @Test
    void executeShouldDeleteExistingTopic() {
        // Create a topic first
        KafkaTopic topic = new KafkaTopicBuilder()
                .withNewMetadata()
                    .withName("topic-to-delete")
                    .withNamespace("kafka")
                    .addToLabels(StrimziLabels.CLUSTER, "my-cluster")
                .endMetadata()
                .withNewSpec()
                    .withPartitions(1)
                    .withReplicas(1)
                .endSpec()
                .build();
        client.resources(KafkaTopic.class).inNamespace("kafka").resource(topic).create();

        // Verify it exists
        assertThat(client.resources(KafkaTopic.class)
                .inNamespace("kafka").withName("topic-to-delete").get()).isNotNull();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "topic-to-delete");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("delete_topic", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Deleted KafkaTopic: kafka/topic-to-delete");

        // Verify it was deleted
        assertThat(client.resources(KafkaTopic.class)
                .inNamespace("kafka").withName("topic-to-delete").get()).isNull();
    }

    @Test
    void executeShouldFailWhenTopicDoesNotExist() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "non-existent-topic");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("delete_topic", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }
}
