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
class ListTopicsToolTest {

    KubernetesClient client;

    private ListTopicsTool tool;

    @BeforeEach
    void setUp() {
        tool = new ListTopicsTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("list_topics");
    }

    @Test
    void getDescriptionShouldReturnCorrectDescription() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().description()).isEqualTo("List Strimzi KafkaTopic resources");
    }

    @Test
    void executeShouldListTopicsInNamespace() {
        createTopic("topic-1", "kafka", "my-cluster", 3, 2);
        createTopic("topic-2", "kafka", "my-cluster", 6, 3);
        createTopic("topic-3", "other-ns", "other-cluster", 1, 1);

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_topics", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 KafkaTopic(s)");
        assertThat(content).contains("topic-1");
        assertThat(content).contains("topic-2");
        assertThat(content).doesNotContain("topic-3");
    }

    @Test
    void executeShouldFilterByKafkaCluster() {
        createTopic("topic-a", "kafka", "cluster-a", 1, 1);
        createTopic("topic-b", "kafka", "cluster-b", 1, 1);
        createTopic("topic-c", "kafka", "cluster-a", 1, 1);

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        args.put("kafkaCluster", "cluster-a");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_topics", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 KafkaTopic(s)");
        assertThat(content).contains("topic-a");
        assertThat(content).contains("topic-c");
        assertThat(content).doesNotContain("topic-b");
    }

    @Test
    void executeShouldListFromAllNamespacesWhenNamespaceNotSpecified() {
        createTopic("topic-ns1", "ns1", "cluster", 1, 1);
        createTopic("topic-ns2", "ns2", "cluster", 1, 1);

        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_topics", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 KafkaTopic(s)");
        assertThat(content).contains("topic-ns1");
        assertThat(content).contains("topic-ns2");
    }

    @Test
    void executeShouldReturnEmptyListWhenNoTopics() {
        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "empty-ns");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_topics", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 0 KafkaTopic(s)");
    }

    @Test
    void executeShouldDisplayPartitionsAndReplicas() {
        createTopic("my-topic", "kafka", "my-cluster", 12, 3);

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_topics", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("partitions: 12");
        assertThat(content).contains("replicas: 3");
    }

    @Test
    void executeShouldDisplayClusterLabel() {
        createTopic("labeled-topic", "kafka", "production-cluster", 1, 1);

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_topics", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("-> production-cluster");
    }

    private void createTopic(String name, String namespace, String cluster, int partitions, int replicas) {
        KafkaTopic topic = new KafkaTopicBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(StrimziLabels.CLUSTER, cluster)
                .endMetadata()
                .withNewSpec()
                    .withPartitions(partitions)
                    .withReplicas(replicas)
                .endSpec()
                .build();
        client.resources(KafkaTopic.class).inNamespace(namespace).resource(topic).create();
    }
}
