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
class DescribeNodePoolToolTest {

    KubernetesClient client;

    private DescribeNodePoolTool tool;

    @BeforeEach
    void setUp() {
        tool = new DescribeNodePoolTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("describe_node_pool");
    }

    @Test
    void executeShouldDescribeNodePool() {
        createNodePool("broker-pool", "kafka", "my-cluster", 3, List.of(ProcessRoles.BROKER));

        Map<String, Object> args = new HashMap<>();
        args.put("name", "broker-pool");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_node_pool", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("KafkaNodePool: kafka/broker-pool");
        assertThat(content).contains("Kafka Cluster: my-cluster");
        assertThat(content).contains("Replicas: 3");
        assertThat(content).contains("Roles:");
    }

    @Test
    void executeShouldFailWhenNodePoolNotFound() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "non-existent");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_node_pool", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }

    @Test
    void executeShouldShowControllerRole() {
        createNodePool("controller-pool", "kafka", "my-cluster", 3, List.of(ProcessRoles.CONTROLLER));

        Map<String, Object> args = new HashMap<>();
        args.put("name", "controller-pool");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_node_pool", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("CONTROLLER");
    }

    @Test
    void executeShouldShowCombinedRoles() {
        createNodePool("combined-pool", "kafka", "my-cluster", 3, List.of(ProcessRoles.BROKER, ProcessRoles.CONTROLLER));

        Map<String, Object> args = new HashMap<>();
        args.put("name", "combined-pool");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_node_pool", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("BROKER");
        assertThat(content).contains("CONTROLLER");
    }

    private void createNodePool(String name, String namespace, String cluster, int replicas, List<ProcessRoles> roles) {
        KafkaNodePool pool = new KafkaNodePoolBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(StrimziLabels.CLUSTER, cluster)
                .endMetadata()
                .withNewSpec()
                    .withReplicas(replicas)
                    .withRoles(roles)
                .endSpec()
                .build();
        client.resources(KafkaNodePool.class).inNamespace(namespace).resource(pool).create();
    }
}