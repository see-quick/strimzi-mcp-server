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
class CreateConnectorToolTest {

    KubernetesClient client;

    private CreateConnectorTool tool;

    @BeforeEach
    void setUp() {
        tool = new CreateConnectorTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("create_connector");
    }

    @Test
    void executeShouldCreateConnector() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "my-connector");
        args.put("namespace", "kafka");
        args.put("connectCluster", "my-connect");
        args.put("className", "org.apache.kafka.connect.file.FileStreamSourceConnector");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("create_connector", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Created KafkaConnector: kafka/my-connector");
        assertThat(content).contains("Connect Cluster: my-connect");
        assertThat(content).contains("Tasks Max: 1");

        // Verify connector was created
        KafkaConnector created = client.resources(KafkaConnector.class).inNamespace("kafka").withName("my-connector").get();
        assertThat(created).isNotNull();
        assertThat(created.getSpec().getClassName()).isEqualTo("org.apache.kafka.connect.file.FileStreamSourceConnector");
    }

    @Test
    void executeShouldFailWhenConnectorAlreadyExists() {
        createConnector("existing-connector", "kafka", "my-connect");

        Map<String, Object> args = new HashMap<>();
        args.put("name", "existing-connector");
        args.put("namespace", "kafka");
        args.put("connectCluster", "my-connect");
        args.put("className", "org.example.Connector");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("create_connector", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("already exists");
    }

    @Test
    void executeShouldCreateConnectorWithCustomConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("topics", "my-topic");
        config.put("file", "/tmp/output.txt");

        Map<String, Object> args = new HashMap<>();
        args.put("name", "configured-connector");
        args.put("namespace", "kafka");
        args.put("connectCluster", "my-connect");
        args.put("className", "org.apache.kafka.connect.file.FileStreamSinkConnector");
        args.put("config", config);
        args.put("tasksMax", 2);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("create_connector", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Tasks Max: 2");
        assertThat(content).contains("Config entries: 2");

        KafkaConnector created = client.resources(KafkaConnector.class).inNamespace("kafka").withName("configured-connector").get();
        assertThat(created.getSpec().getConfig()).containsEntry("topics", "my-topic");
    }

    @Test
    void executeShouldCreatePausedConnector() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "paused-connector");
        args.put("namespace", "kafka");
        args.put("connectCluster", "my-connect");
        args.put("className", "org.example.Connector");
        args.put("pause", true);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("create_connector", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Initial State: paused");

        KafkaConnector created = client.resources(KafkaConnector.class).inNamespace("kafka").withName("paused-connector").get();
        assertThat(created.getSpec().getPause()).isTrue();
    }

    private void createConnector(String name, String namespace, String connectCluster) {
        KafkaConnector connector = new KafkaConnectorBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(StrimziLabels.CLUSTER, connectCluster)
                .endMetadata()
                .withNewSpec()
                    .withClassName("org.example.Connector")
                    .withTasksMax(1)
                .endSpec()
                .build();
        client.resources(KafkaConnector.class).inNamespace(namespace).resource(connector).create();
    }
}
