package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.seequick.mcp.tool.StrimziLabels;
import io.strimzi.api.kafka.model.rebalance.KafkaRebalance;
import io.strimzi.api.kafka.model.rebalance.KafkaRebalanceBuilder;
import io.strimzi.api.kafka.model.rebalance.KafkaRebalanceMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class DescribeRebalanceToolTest {

    KubernetesClient client;

    private DescribeRebalanceTool tool;

    @BeforeEach
    void setUp() {
        tool = new DescribeRebalanceTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("describe_rebalance");
    }

    @Test
    void executeShouldDescribeRebalance() {
        createRebalance("my-rebalance", "kafka", "my-cluster");

        Map<String, Object> args = new HashMap<>();
        args.put("name", "my-rebalance");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_rebalance", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("KafkaRebalance: kafka/my-rebalance");
        assertThat(content).contains("Kafka Cluster: my-cluster");
    }

    @Test
    void executeShouldFailWhenRebalanceNotFound() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "non-existent");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_rebalance", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }

    @Test
    void executeShouldShowRebalanceMode() {
        KafkaRebalance rebalance = new KafkaRebalanceBuilder()
                .withNewMetadata()
                    .withName("add-broker-rebalance")
                    .withNamespace("kafka")
                    .addToLabels(StrimziLabels.CLUSTER, "my-cluster")
                .endMetadata()
                .withNewSpec()
                    .withMode(KafkaRebalanceMode.ADD_BROKERS)
                    .withBrokers(3, 4)
                .endSpec()
                .build();
        client.resources(KafkaRebalance.class).inNamespace("kafka").resource(rebalance).create();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "add-broker-rebalance");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_rebalance", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Mode:");
        assertThat(content).contains("Brokers:");
    }

    @Test
    void executeShouldShowGoals() {
        KafkaRebalance rebalance = new KafkaRebalanceBuilder()
                .withNewMetadata()
                    .withName("goals-rebalance")
                    .withNamespace("kafka")
                    .addToLabels(StrimziLabels.CLUSTER, "my-cluster")
                .endMetadata()
                .withNewSpec()
                    .withGoals(List.of("RackAwareGoal", "ReplicaCapacityGoal"))
                .endSpec()
                .build();
        client.resources(KafkaRebalance.class).inNamespace("kafka").resource(rebalance).create();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "goals-rebalance");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_rebalance", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Goals:");
        assertThat(content).contains("RackAwareGoal");
        assertThat(content).contains("ReplicaCapacityGoal");
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