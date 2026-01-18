package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.strimzi.api.kafka.model.connect.KafkaConnect;
import io.strimzi.api.kafka.model.connect.KafkaConnectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class DescribeKafkaConnectToolTest {

    KubernetesClient client;

    private DescribeKafkaConnectTool tool;

    @BeforeEach
    void setUp() {
        tool = new DescribeKafkaConnectTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("describe_kafka_connect");
    }

    @Test
    void executeShouldDescribeKafkaConnect() {
        createKafkaConnect("my-connect", "kafka", "my-cluster-kafka-bootstrap:9092", 3);

        Map<String, Object> args = new HashMap<>();
        args.put("name", "my-connect");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_kafka_connect", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("KafkaConnect: kafka/my-connect");
        assertThat(content).contains("Bootstrap Servers: my-cluster-kafka-bootstrap:9092");
        assertThat(content).contains("Replicas: 3");
    }

    @Test
    void executeShouldFailWhenConnectNotFound() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "non-existent");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_kafka_connect", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }

    @Test
    void executeShouldShowVersion() {
        KafkaConnect connect = new KafkaConnectBuilder()
                .withNewMetadata()
                    .withName("versioned-connect")
                    .withNamespace("kafka")
                .endMetadata()
                .withNewSpec()
                    .withReplicas(2)
                    .withBootstrapServers("bootstrap:9092")
                    .withVersion("3.6.0")
                .endSpec()
                .build();
        client.resources(KafkaConnect.class).inNamespace("kafka").resource(connect).create();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "versioned-connect");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_kafka_connect", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Version: 3.6.0");
    }

    @Test
    void executeShouldShowConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("group.id", "my-connect-group");
        config.put("offset.storage.topic", "connect-offsets");

        KafkaConnect connect = new KafkaConnectBuilder()
                .withNewMetadata()
                    .withName("configured-connect")
                    .withNamespace("kafka")
                .endMetadata()
                .withNewSpec()
                    .withReplicas(1)
                    .withBootstrapServers("bootstrap:9092")
                    .withConfig(config)
                .endSpec()
                .build();
        client.resources(KafkaConnect.class).inNamespace("kafka").resource(connect).create();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "configured-connect");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_kafka_connect", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Configuration:");
        assertThat(content).contains("group.id: my-connect-group");
    }

    private void createKafkaConnect(String name, String namespace, String bootstrap, int replicas) {
        KafkaConnect connect = new KafkaConnectBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withReplicas(replicas)
                    .withBootstrapServers(bootstrap)
                .endSpec()
                .build();
        client.resources(KafkaConnect.class).inNamespace(namespace).resource(connect).create();
    }
}
