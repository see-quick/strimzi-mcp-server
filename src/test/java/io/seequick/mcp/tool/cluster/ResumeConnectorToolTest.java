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
class ResumeConnectorToolTest {

    KubernetesClient client;

    private ResumeConnectorTool tool;

    @BeforeEach
    void setUp() {
        tool = new ResumeConnectorTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("resume_connector");
    }

    @Test
    void executeShouldResumeConnector() {
        createConnector("paused-connector", "kafka", "my-connect", true);

        Map<String, Object> args = new HashMap<>();
        args.put("name", "paused-connector");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("resume_connector", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Resumed KafkaConnector: kafka/paused-connector");

        // Verify connector is now resumed
        KafkaConnector updated = client.resources(KafkaConnector.class).inNamespace("kafka").withName("paused-connector").get();
        assertThat(updated.getSpec().getPause()).isFalse();
    }

    @Test
    void executeShouldFailWhenConnectorNotFound() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "non-existent");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("resume_connector", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }

    @Test
    void executeShouldReportAlreadyRunning() {
        // Create connector without pause (null means not paused)
        KafkaConnector connector = new KafkaConnectorBuilder()
                .withNewMetadata()
                    .withName("running-connector")
                    .withNamespace("kafka")
                    .addToLabels(StrimziLabels.CLUSTER, "my-connect")
                .endMetadata()
                .withNewSpec()
                    .withClassName("org.example.Connector")
                    .withTasksMax(1)
                    .withPause(false) // Explicitly set to false
                .endSpec()
                .build();
        client.resources(KafkaConnector.class).inNamespace("kafka").resource(connector).create();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "running-connector");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("resume_connector", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("already running");
    }

    private void createConnector(String name, String namespace, String connectCluster, boolean paused) {
        var builder = new KafkaConnectorBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(StrimziLabels.CLUSTER, connectCluster)
                .endMetadata()
                .withNewSpec()
                    .withClassName("org.example.Connector")
                    .withTasksMax(1);

        if (paused) {
            builder.withPause(true);
        }

        KafkaConnector connector = builder.endSpec().build();
        client.resources(KafkaConnector.class).inNamespace(namespace).resource(connector).create();
    }
}
