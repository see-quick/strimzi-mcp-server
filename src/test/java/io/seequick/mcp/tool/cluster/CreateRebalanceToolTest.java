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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class CreateRebalanceToolTest {

    KubernetesClient client;

    private CreateRebalanceTool tool;

    @BeforeEach
    void setUp() {
        tool = new CreateRebalanceTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("create_rebalance");
    }

    @Test
    void executeShouldCreateRebalance() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "my-rebalance");
        args.put("namespace", "kafka");
        args.put("kafkaCluster", "my-cluster");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("create_rebalance", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Created KafkaRebalance: kafka/my-rebalance");
        assertThat(content).contains("Kafka Cluster: my-cluster");
        assertThat(content).contains("Mode: full");

        // Verify rebalance was created
        KafkaRebalance created = client.resources(KafkaRebalance.class).inNamespace("kafka").withName("my-rebalance").get();
        assertThat(created).isNotNull();
    }

    @Test
    void executeShouldFailWhenRebalanceAlreadyExists() {
        createRebalance("existing-rebalance", "kafka", "my-cluster");

        Map<String, Object> args = new HashMap<>();
        args.put("name", "existing-rebalance");
        args.put("namespace", "kafka");
        args.put("kafkaCluster", "my-cluster");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("create_rebalance", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("already exists");
    }

    @Test
    void executeShouldCreateAddBrokersRebalance() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "add-brokers-rebalance");
        args.put("namespace", "kafka");
        args.put("kafkaCluster", "my-cluster");
        args.put("mode", "add-brokers");
        args.put("brokers", List.of(3, 4));
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("create_rebalance", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Mode: add-brokers");
        assertThat(content).contains("Brokers:");
    }

    @Test
    void executeShouldCreateRebalanceWithGoals() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "goals-rebalance");
        args.put("namespace", "kafka");
        args.put("kafkaCluster", "my-cluster");
        args.put("goals", List.of("RackAwareGoal", "ReplicaCapacityGoal"));
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("create_rebalance", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("2 custom goals");
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
