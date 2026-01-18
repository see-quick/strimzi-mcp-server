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
class ListConnectorsToolTest {

    KubernetesClient client;

    private ListConnectorsTool tool;

    @BeforeEach
    void setUp() {
        tool = new ListConnectorsTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("list_connectors");
    }

    @Test
    void executeShouldListConnectorsInNamespace() {
        createConnector("connector-1", "kafka", "my-connect", "FileStreamSource");
        createConnector("connector-2", "kafka", "my-connect", "FileStreamSink");
        createConnector("connector-3", "other-ns", "other-connect", "JdbcSource");

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_connectors", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 KafkaConnector(s)");
        assertThat(content).contains("connector-1");
        assertThat(content).contains("connector-2");
        assertThat(content).doesNotContain("connector-3");
    }

    @Test
    void executeShouldFilterByConnectCluster() {
        createConnector("conn-a", "kafka", "connect-a", "FileStreamSource");
        createConnector("conn-b", "kafka", "connect-b", "FileStreamSource");
        createConnector("conn-c", "kafka", "connect-a", "FileStreamSink");

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        args.put("connectCluster", "connect-a");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_connectors", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 KafkaConnector(s)");
        assertThat(content).contains("conn-a");
        assertThat(content).contains("conn-c");
        assertThat(content).doesNotContain("conn-b");
    }

    @Test
    void executeShouldListFromAllNamespacesWhenNotSpecified() {
        createConnector("conn-ns1", "ns1", "connect", "FileStreamSource");
        createConnector("conn-ns2", "ns2", "connect", "FileStreamSink");

        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_connectors", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 KafkaConnector(s)");
        assertThat(content).contains("conn-ns1");
        assertThat(content).contains("conn-ns2");
    }

    @Test
    void executeShouldDisplayConnectorClass() {
        createConnector("jdbc-connector", "kafka", "my-connect", "io.debezium.connector.mysql.MySqlConnector");

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_connectors", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("io.debezium.connector.mysql.MySqlConnector");
    }

    @Test
    void executeShouldReturnEmptyListWhenNoConnectors() {
        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "empty-ns");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_connectors", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 0 KafkaConnector(s)");
    }

    private void createConnector(String name, String namespace, String cluster, String className) {
        KafkaConnector connector = new KafkaConnectorBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(StrimziLabels.CLUSTER, cluster)
                .endMetadata()
                .withNewSpec()
                    .withClassName(className)
                    .withTasksMax(1)
                .endSpec()
                .build();
        client.resources(KafkaConnector.class).inNamespace(namespace).resource(connector).create();
    }
}
