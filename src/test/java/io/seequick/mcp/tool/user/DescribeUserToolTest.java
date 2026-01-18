package io.seequick.mcp.tool.user;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.seequick.mcp.tool.StrimziLabels;
import io.strimzi.api.kafka.model.user.KafkaUser;
import io.strimzi.api.kafka.model.user.KafkaUserBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class DescribeUserToolTest {

    KubernetesClient client;

    private DescribeUserTool tool;

    @BeforeEach
    void setUp() {
        tool = new DescribeUserTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("describe_user");
    }

    @Test
    void executeShouldDescribeUser() {
        createUser("test-user", "kafka", "my-cluster");

        Map<String, Object> args = new HashMap<>();
        args.put("name", "test-user");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_user", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("KafkaUser: kafka/test-user");
        assertThat(content).contains("Kafka Cluster: my-cluster");
        assertThat(content).contains("Authentication:");
        assertThat(content).contains("scram-sha-512");
    }

    @Test
    void executeShouldFailWhenUserNotFound() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "non-existent");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_user", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }

    @Test
    void executeShouldHandleUserWithQuotas() {
        KafkaUser user = new KafkaUserBuilder()
                .withNewMetadata()
                    .withName("quota-user")
                    .withNamespace("kafka")
                    .addToLabels(StrimziLabels.CLUSTER, "my-cluster")
                .endMetadata()
                .withNewSpec()
                    .withNewKafkaUserScramSha512ClientAuthentication()
                    .endKafkaUserScramSha512ClientAuthentication()
                    .withNewQuotas()
                        .withProducerByteRate(1024000)
                        .withConsumerByteRate(2048000)
                    .endQuotas()
                .endSpec()
                .build();
        client.resources(KafkaUser.class).inNamespace("kafka").resource(user).create();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "quota-user");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_user", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Quotas:");
        assertThat(content).contains("Producer Byte Rate: 1024000");
        assertThat(content).contains("Consumer Byte Rate: 2048000");
    }

    private void createUser(String name, String namespace, String cluster) {
        KafkaUser user = new KafkaUserBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(StrimziLabels.CLUSTER, cluster)
                .endMetadata()
                .withNewSpec()
                    .withNewKafkaUserScramSha512ClientAuthentication()
                    .endKafkaUserScramSha512ClientAuthentication()
                .endSpec()
                .build();
        client.resources(KafkaUser.class).inNamespace(namespace).resource(user).create();
    }
}