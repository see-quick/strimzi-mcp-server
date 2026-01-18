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
class ListKafkaConnectsToolTest {

    KubernetesClient client;

    private ListKafkaConnectsTool tool;

    @BeforeEach
    void setUp() {
        tool = new ListKafkaConnectsTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("list_kafka_connects");
    }

    @Test
    void executeShouldListKafkaConnectsInNamespace() {
        createKafkaConnect("connect-1", "kafka", 3, "my-cluster-kafka-bootstrap:9092");
        createKafkaConnect("connect-2", "kafka", 2, "my-cluster-kafka-bootstrap:9092");
        createKafkaConnect("connect-3", "other-ns", 1, "other-bootstrap:9092");

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_kafka_connects", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 KafkaConnect cluster(s)");
        assertThat(content).contains("connect-1");
        assertThat(content).contains("connect-2");
        assertThat(content).doesNotContain("connect-3");
    }

    @Test
    void executeShouldListFromAllNamespacesWhenNotSpecified() {
        createKafkaConnect("connect-ns1", "ns1", 1, "bootstrap:9092");
        createKafkaConnect("connect-ns2", "ns2", 1, "bootstrap:9092");

        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_kafka_connects", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 KafkaConnect cluster(s)");
        assertThat(content).contains("connect-ns1");
        assertThat(content).contains("connect-ns2");
    }

    @Test
    void executeShouldDisplayReplicasAndBootstrap() {
        createKafkaConnect("my-connect", "kafka", 5, "production-kafka:9093");

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_kafka_connects", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("[replicas: 5]");
        assertThat(content).contains("production-kafka:9093");
    }

    @Test
    void executeShouldReturnEmptyListWhenNoConnects() {
        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "empty-ns");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_kafka_connects", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 0 KafkaConnect cluster(s)");
    }

    private void createKafkaConnect(String name, String namespace, int replicas, String bootstrap) {
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
