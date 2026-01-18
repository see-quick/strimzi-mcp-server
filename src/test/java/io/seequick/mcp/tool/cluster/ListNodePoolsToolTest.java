package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.seequick.mcp.tool.StrimziLabels;
import io.strimzi.api.kafka.model.nodepool.KafkaNodePool;
import io.strimzi.api.kafka.model.nodepool.KafkaNodePoolBuilder;
import io.strimzi.api.kafka.model.nodepool.ProcessRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class ListNodePoolsToolTest {

    KubernetesClient client;

    private ListNodePoolsTool tool;

    @BeforeEach
    void setUp() {
        tool = new ListNodePoolsTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("list_node_pools");
    }

    @Test
    void executeShouldListNodePoolsInNamespace() {
        createNodePool("broker-pool", "kafka", "my-cluster", 3);
        createNodePool("controller-pool", "kafka", "my-cluster", 3);
        createNodePool("other-pool", "other-ns", "other-cluster", 1);

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_node_pools", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 KafkaNodePool(s)");
        assertThat(content).contains("broker-pool");
        assertThat(content).contains("controller-pool");
        assertThat(content).doesNotContain("other-pool");
    }

    @Test
    void executeShouldFilterByKafkaCluster() {
        createNodePool("pool-a", "kafka", "cluster-a", 3);
        createNodePool("pool-b", "kafka", "cluster-b", 3);

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        args.put("kafkaCluster", "cluster-a");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_node_pools", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 1 KafkaNodePool(s)");
        assertThat(content).contains("pool-a");
        assertThat(content).doesNotContain("pool-b");
    }

    @Test
    void executeShouldListFromAllNamespacesWhenNotSpecified() {
        createNodePool("pool-ns1", "ns1", "cluster", 3);
        createNodePool("pool-ns2", "ns2", "cluster", 3);

        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_node_pools", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 KafkaNodePool(s)");
        assertThat(content).contains("pool-ns1");
        assertThat(content).contains("pool-ns2");
    }

    @Test
    void executeShouldReturnEmptyListWhenNoNodePools() {
        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "empty-ns");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_node_pools", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 0 KafkaNodePool(s)");
    }

    private void createNodePool(String name, String namespace, String cluster, int replicas) {
        KafkaNodePool pool = new KafkaNodePoolBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(StrimziLabels.CLUSTER, cluster)
                .endMetadata()
                .withNewSpec()
                    .withReplicas(replicas)
                    .withRoles(List.of(ProcessRoles.BROKER))
                .endSpec()
                .build();
        client.resources(KafkaNodePool.class).inNamespace(namespace).resource(pool).create();
    }
}
