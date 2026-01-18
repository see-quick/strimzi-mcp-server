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
class DeleteConnectorToolTest {

    KubernetesClient client;

    private DeleteConnectorTool tool;

    @BeforeEach
    void setUp() {
        tool = new DeleteConnectorTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("delete_connector");
    }

    @Test
    void executeShouldDeleteConnector() {
        createConnector("to-delete", "kafka", "my-connect");

        Map<String, Object> args = new HashMap<>();
        args.put("name", "to-delete");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("delete_connector", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Deleted KafkaConnector: kafka/to-delete");
        assertThat(content).contains("Connect Cluster: my-connect");

        // Verify connector was deleted
        KafkaConnector deleted = client.resources(KafkaConnector.class).inNamespace("kafka").withName("to-delete").get();
        assertThat(deleted).isNull();
    }

    @Test
    void executeShouldFailWhenConnectorNotFound() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "non-existent");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("delete_connector", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }

    @Test
    void executeShouldShowClassName() {
        createConnectorWithClass("class-connector", "kafka", "my-connect", "org.example.MyConnector");

        Map<String, Object> args = new HashMap<>();
        args.put("name", "class-connector");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("delete_connector", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Class: org.example.MyConnector");
    }

    private void createConnector(String name, String namespace, String connectCluster) {
        createConnectorWithClass(name, namespace, connectCluster, "org.example.DefaultConnector");
    }

    private void createConnectorWithClass(String name, String namespace, String connectCluster, String className) {
        KafkaConnector connector = new KafkaConnectorBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(StrimziLabels.CLUSTER, connectCluster)
                .endMetadata()
                .withNewSpec()
                    .withClassName(className)
                    .withTasksMax(1)
                .endSpec()
                .build();
        client.resources(KafkaConnector.class).inNamespace(namespace).resource(connector).create();
    }
}
