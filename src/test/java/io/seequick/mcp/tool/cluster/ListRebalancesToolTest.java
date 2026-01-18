package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.seequick.mcp.tool.StrimziLabels;
import io.strimzi.api.kafka.model.rebalance.KafkaRebalance;
import io.strimzi.api.kafka.model.rebalance.KafkaRebalanceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class ListRebalancesToolTest {

    KubernetesClient client;

    private ListRebalancesTool tool;

    @BeforeEach
    void setUp() {
        tool = new ListRebalancesTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("list_rebalances");
    }

    @Test
    void executeShouldListRebalancesInNamespace() {
        createRebalance("rebalance-1", "kafka", "my-cluster");
        createRebalance("rebalance-2", "kafka", "my-cluster");
        createRebalance("rebalance-3", "other-ns", "other-cluster");

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_rebalances", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 KafkaRebalance(s)");
        assertThat(content).contains("rebalance-1");
        assertThat(content).contains("rebalance-2");
        assertThat(content).doesNotContain("rebalance-3");
    }

    @Test
    void executeShouldFilterByKafkaCluster() {
        createRebalance("reb-a", "kafka", "cluster-a");
        createRebalance("reb-b", "kafka", "cluster-b");
        createRebalance("reb-c", "kafka", "cluster-a");

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        args.put("kafkaCluster", "cluster-a");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_rebalances", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 KafkaRebalance(s)");
        assertThat(content).contains("reb-a");
        assertThat(content).contains("reb-c");
        assertThat(content).doesNotContain("reb-b");
    }

    @Test
    void executeShouldListFromAllNamespacesWhenNotSpecified() {
        createRebalance("reb-ns1", "ns1", "cluster");
        createRebalance("reb-ns2", "ns2", "cluster");

        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_rebalances", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 KafkaRebalance(s)");
        assertThat(content).contains("reb-ns1");
        assertThat(content).contains("reb-ns2");
    }

    @Test
    void executeShouldReturnEmptyListWhenNoRebalances() {
        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "empty-ns");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_rebalances", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 0 KafkaRebalance(s)");
    }

    private void createRebalance(String name, String namespace, String cluster) {
        KafkaRebalance rebalance = new KafkaRebalanceBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(StrimziLabels.CLUSTER, cluster)
                .endMetadata()
                .withNewSpec()
                .endSpec()
                .build();
        client.resources(KafkaRebalance.class).inNamespace(namespace).resource(rebalance).create();
    }
}
