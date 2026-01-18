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
class ListBridgesToolTest {

    KubernetesClient client;

    private ListBridgesTool tool;

    @BeforeEach
    void setUp() {
        tool = new ListBridgesTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("list_bridges");
    }

    @Test
    void executeShouldListBridgesInNamespace() {
        createBridge("bridge-1", "kafka", "my-cluster-kafka-bootstrap:9092");
        createBridge("bridge-2", "kafka", "my-cluster-kafka-bootstrap:9092");
        createBridge("bridge-3", "other-ns", "other-bootstrap:9092");

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_bridges", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 KafkaBridge(s)");
        assertThat(content).contains("bridge-1");
        assertThat(content).contains("bridge-2");
        assertThat(content).doesNotContain("bridge-3");
    }

    @Test
    void executeShouldListFromAllNamespacesWhenNotSpecified() {
        createBridge("bridge-ns1", "ns1", "bootstrap:9092");
        createBridge("bridge-ns2", "ns2", "bootstrap:9092");

        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_bridges", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 KafkaBridge(s)");
        assertThat(content).contains("bridge-ns1");
        assertThat(content).contains("bridge-ns2");
    }

    @Test
    void executeShouldReturnEmptyListWhenNoBridges() {
        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "empty-ns");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_bridges", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 0 KafkaBridge(s)");
    }

    private void createBridge(String name, String namespace, String bootstrap) {
        KafkaBridge bridge = new KafkaBridgeBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withReplicas(1)
                    .withBootstrapServers(bootstrap)
                .endSpec()
                .build();
        client.resources(KafkaBridge.class).inNamespace(namespace).resource(bridge).create();
    }
}
