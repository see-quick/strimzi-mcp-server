package io.seequick.mcp.tool.user;

import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
import io.fabric8.kubernetes.api.model.ContainerStateBuilder;
import io.fabric8.kubernetes.api.model.ContainerStateRunningBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class GetUserOperatorStatusToolTest {

    KubernetesClient client;

    private GetUserOperatorStatusTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetUserOperatorStatusTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("get_user_operator_status");
    }

    @Test
    void executeShouldReturnNotFoundWhenNoPod() {
        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        args.put("kafkaCluster", "my-cluster");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_user_operator_status", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("User Operator Status");
        assertThat(content).contains("No entity-operator pod found");
    }

    @Test
    void executeShouldReturnPodStatusWhenPodExists() {
        createEntityOperatorPod("my-cluster", "kafka");

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        args.put("kafkaCluster", "my-cluster");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_user_operator_status", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("User Operator Status");
        assertThat(content).contains("my-cluster-entity-operator");
        assertThat(content).contains("Phase: Running");
    }

    @Test
    void executeShouldShowUserOperatorContainerStatus() {
        createEntityOperatorPod("my-cluster", "kafka");

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        args.put("kafkaCluster", "my-cluster");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_user_operator_status", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("User Operator Container:");
        assertThat(content).contains("Ready: true");
        assertThat(content).contains("Restarts: 0");
    }

    private void createEntityOperatorPod(String clusterName, String namespace) {
        var pod = new PodBuilder()
                .withNewMetadata()
                    .withName(clusterName + "-entity-operator-abc123")
                    .withNamespace(namespace)
                    .addToLabels("strimzi.io/cluster", clusterName)
                    .addToLabels("strimzi.io/kind", "Kafka")
                    .addToLabels("strimzi.io/name", clusterName + "-entity-operator")
                .endMetadata()
                .withNewSpec()
                .endSpec()
                .withStatus(new PodStatusBuilder()
                    .withPhase("Running")
                    .withContainerStatuses(List.of(
                        new ContainerStatusBuilder()
                            .withName("user-operator")
                            .withReady(true)
                            .withRestartCount(0)
                            .withState(new ContainerStateBuilder()
                                .withRunning(new ContainerStateRunningBuilder().build())
                                .build())
                            .build(),
                        new ContainerStatusBuilder()
                            .withName("topic-operator")
                            .withReady(true)
                            .withRestartCount(0)
                            .withState(new ContainerStateBuilder()
                                .withRunning(new ContainerStateRunningBuilder().build())
                                .build())
                            .build()
                    ))
                    .build())
                .build();
        client.pods().inNamespace(namespace).resource(pod).create();
    }
}
