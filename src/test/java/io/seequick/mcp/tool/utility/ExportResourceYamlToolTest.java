package io.seequick.mcp.tool.utility;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.seequick.mcp.tool.StrimziLabels;
import io.strimzi.api.kafka.model.kafka.Kafka;
import io.strimzi.api.kafka.model.kafka.KafkaBuilder;
import io.strimzi.api.kafka.model.topic.KafkaTopic;
import io.strimzi.api.kafka.model.topic.KafkaTopicBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class ExportResourceYamlToolTest {

    KubernetesClient client;

    private ExportResourceYamlTool tool;

    @BeforeEach
    void setUp() {
        tool = new ExportResourceYamlTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("export_resource_yaml");
    }

    @Test
    void executeShouldExportKafkaAsYaml() {
        createKafka("my-cluster", "kafka");

        Map<String, Object> args = new HashMap<>();
        args.put("kind", "Kafka");
        args.put("name", "my-cluster");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("export_resource_yaml", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("# Kafka: kafka/my-cluster");
        assertThat(content).contains("apiVersion:");
        assertThat(content).contains("kind: Kafka");
        assertThat(content).contains("my-cluster");
    }

    @Test
    void executeShouldExportTopicAsYaml() {
        createTopic("my-topic", "kafka", "my-cluster");

        Map<String, Object> args = new HashMap<>();
        args.put("kind", "KafkaTopic");
        args.put("name", "my-topic");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("export_resource_yaml", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("# KafkaTopic: kafka/my-topic");
        assertThat(content).contains("kind: KafkaTopic");
    }

    @Test
    void executeShouldFailWhenResourceNotFound() {
        Map<String, Object> args = new HashMap<>();
        args.put("kind", "Kafka");
        args.put("name", "non-existent");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("export_resource_yaml", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }

    @Test
    void executeShouldIncludeStatusWhenRequested() {
        createKafka("status-cluster", "kafka");

        Map<String, Object> args = new HashMap<>();
        args.put("kind", "Kafka");
        args.put("name", "status-cluster");
        args.put("namespace", "kafka");
        args.put("includeStatus", true);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("export_resource_yaml", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("(with status)");
    }

    @Test
    void executeShouldFailForUnsupportedKind() {
        Map<String, Object> args = new HashMap<>();
        args.put("kind", "UnsupportedKind");
        args.put("name", "test");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("export_resource_yaml", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }

    private void createKafka(String name, String namespace) {
        Kafka kafka = new KafkaBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withNewKafka()
                        .withReplicas(3)
                    .endKafka()
                .endSpec()
                .build();
        client.resources(Kafka.class).inNamespace(namespace).resource(kafka).create();
    }

    private void createTopic(String name, String namespace, String cluster) {
        KafkaTopic topic = new KafkaTopicBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(StrimziLabels.CLUSTER, cluster)
                .endMetadata()
                .withNewSpec()
                    .withPartitions(3)
                    .withReplicas(3)
                .endSpec()
                .build();
        client.resources(KafkaTopic.class).inNamespace(namespace).resource(topic).create();
    }
}
