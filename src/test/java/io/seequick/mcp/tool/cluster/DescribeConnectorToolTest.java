package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.seequick.mcp.tool.StrimziLabels;
import io.strimzi.api.kafka.model.connector.KafkaConnector;
import io.strimzi.api.kafka.model.connector.KafkaConnectorBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class DescribeConnectorToolTest {

    KubernetesClient client;

    private DescribeConnectorTool tool;

    @BeforeEach
    void setUp() {
        tool = new DescribeConnectorTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("describe_connector");
    }

    @Test
    void executeShouldDescribeConnector() {
        createConnector("my-connector", "kafka", "my-connect", "org.apache.kafka.connect.file.FileStreamSourceConnector", 1);

        Map<String, Object> args = new HashMap<>();
        args.put("name", "my-connector");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_connector", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("KafkaConnector: kafka/my-connector");
        assertThat(content).contains("Kafka Connect Cluster: my-connect");
        assertThat(content).contains("Class: org.apache.kafka.connect.file.FileStreamSourceConnector");
        assertThat(content).contains("Tasks Max: 1");
    }

    @Test
    void executeShouldFailWhenConnectorNotFound() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "non-existent");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_connector", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }

    @Test
    void executeShouldShowPausedStatus() {
        KafkaConnector connector = new KafkaConnectorBuilder()
                .withNewMetadata()
                    .withName("paused-connector")
                    .withNamespace("kafka")
                    .addToLabels(StrimziLabels.CLUSTER, "my-connect")
                .endMetadata()
                .withNewSpec()
                    .withClassName("org.example.SinkConnector")
                    .withTasksMax(2)
                    .withPause(true)
                .endSpec()
                .build();
        client.resources(KafkaConnector.class).inNamespace("kafka").resource(connector).create();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "paused-connector");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_connector", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Paused: true");
    }

    @Test
    void executeShouldShowConnectorConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("topics", "my-topic");
        config.put("file", "/tmp/output.txt");

        KafkaConnector connector = new KafkaConnectorBuilder()
                .withNewMetadata()
                    .withName("configured-connector")
                    .withNamespace("kafka")
                    .addToLabels(StrimziLabels.CLUSTER, "my-connect")
                .endMetadata()
                .withNewSpec()
                    .withClassName("org.apache.kafka.connect.file.FileStreamSinkConnector")
                    .withTasksMax(1)
                    .withConfig(config)
                .endSpec()
                .build();
        client.resources(KafkaConnector.class).inNamespace("kafka").resource(connector).create();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "configured-connector");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_connector", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Configuration:");
        assertThat(content).contains("topics: my-topic");
        assertThat(content).contains("file: /tmp/output.txt");
    }

    private void createConnector(String name, String namespace, String connectCluster, String className, int tasksMax) {
        KafkaConnector connector = new KafkaConnectorBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(StrimziLabels.CLUSTER, connectCluster)
                .endMetadata()
                .withNewSpec()
                    .withClassName(className)
                    .withTasksMax(tasksMax)
                .endSpec()
                .build();
        client.resources(KafkaConnector.class).inNamespace(namespace).resource(connector).create();
    }
}