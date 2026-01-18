package io.seequick.mcp.tool.kafka;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.strimzi.api.kafka.model.kafka.Kafka;
import io.strimzi.api.kafka.model.kafka.KafkaBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class GetKafkaStatusToolTest {

    KubernetesClient client;

    private GetKafkaStatusTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetKafkaStatusTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("get_kafka_status");
    }

    @Test
    void executeShouldReturnKafkaStatus() {
        createKafka("my-cluster", "kafka", 3);

        Map<String, Object> args = new HashMap<>();
        args.put("name", "my-cluster");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_kafka_status", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Kafka Cluster: kafka/my-cluster");
        assertThat(content).contains("Replicas: 3");
    }

    @Test
    void executeShouldFailWhenKafkaNotFound() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "non-existent");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_kafka_status", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }

    @Test
    void executeShouldShowVersionIfSet() {
        Kafka kafka = new KafkaBuilder()
                .withNewMetadata()
                    .withName("versioned-cluster")
                    .withNamespace("kafka")
                .endMetadata()
                .withNewSpec()
                    .withNewKafka()
                        .withReplicas(3)
                        .withVersion("3.6.0")
                    .endKafka()
                .endSpec()
                .build();
        client.resources(Kafka.class).inNamespace("kafka").resource(kafka).create();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "versioned-cluster");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_kafka_status", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Version: 3.6.0");
    }

    private void createKafka(String name, String namespace, int replicas) {
        Kafka kafka = new KafkaBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withNewKafka()
                        .withReplicas(replicas)
                    .endKafka()
                .endSpec()
                .build();
        client.resources(Kafka.class).inNamespace(namespace).resource(kafka).create();
    }
}