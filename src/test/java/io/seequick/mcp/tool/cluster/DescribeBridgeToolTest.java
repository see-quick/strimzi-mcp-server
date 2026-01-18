package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.strimzi.api.kafka.model.bridge.KafkaBridge;
import io.strimzi.api.kafka.model.bridge.KafkaBridgeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class DescribeBridgeToolTest {

    KubernetesClient client;

    private DescribeBridgeTool tool;

    @BeforeEach
    void setUp() {
        tool = new DescribeBridgeTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("describe_bridge");
    }

    @Test
    void executeShouldDescribeBridge() {
        createBridge("my-bridge", "kafka", "my-cluster-kafka-bootstrap:9092", 1);

        Map<String, Object> args = new HashMap<>();
        args.put("name", "my-bridge");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_bridge", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("KafkaBridge: kafka/my-bridge");
        assertThat(content).contains("Bootstrap Servers: my-cluster-kafka-bootstrap:9092");
        assertThat(content).contains("Replicas: 1");
    }

    @Test
    void executeShouldFailWhenBridgeNotFound() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "non-existent");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_bridge", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }

    @Test
    void executeShouldShowHttpConfig() {
        KafkaBridge bridge = new KafkaBridgeBuilder()
                .withNewMetadata()
                    .withName("http-bridge")
                    .withNamespace("kafka")
                .endMetadata()
                .withNewSpec()
                    .withReplicas(2)
                    .withBootstrapServers("bootstrap:9092")
                    .withNewHttp()
                        .withPort(8080)
                    .endHttp()
                .endSpec()
                .build();
        client.resources(KafkaBridge.class).inNamespace("kafka").resource(bridge).create();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "http-bridge");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_bridge", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("HTTP Configuration:");
        assertThat(content).contains("Port: 8080");
    }

    private void createBridge(String name, String namespace, String bootstrap, int replicas) {
        KafkaBridge bridge = new KafkaBridgeBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withReplicas(replicas)
                    .withBootstrapServers(bootstrap)
                .endSpec()
                .build();
        client.resources(KafkaBridge.class).inNamespace(namespace).resource(bridge).create();
    }
}