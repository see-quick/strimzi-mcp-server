package io.seequick.mcp.tool.user;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.seequick.mcp.tool.StrimziLabels;
import io.strimzi.api.kafka.model.user.KafkaUser;
import io.strimzi.api.kafka.model.user.KafkaUserBuilder;
import io.strimzi.api.kafka.model.user.KafkaUserScramSha512ClientAuthentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class CreateUserToolTest {

    KubernetesClient client;

    private CreateUserTool tool;

    @BeforeEach
    void setUp() {
        tool = new CreateUserTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("create_user");
    }

    @Test
    void executeShouldCreateUserWithDefaultAuth() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "my-user");
        args.put("namespace", "kafka");
        args.put("kafkaCluster", "my-cluster");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("create_user", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Created KafkaUser: kafka/my-user");
        assertThat(content).contains("Authentication: scram-sha-512");

        KafkaUser created = client.resources(KafkaUser.class)
                .inNamespace("kafka").withName("my-user").get();
        assertThat(created).isNotNull();
        assertThat(created.getSpec().getAuthentication().getType()).isEqualTo("scram-sha-512");
    }

    @Test
    void executeShouldCreateUserWithTlsAuth() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "tls-user");
        args.put("namespace", "kafka");
        args.put("kafkaCluster", "my-cluster");
        args.put("authentication", "tls");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("create_user", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Authentication: tls");

        KafkaUser created = client.resources(KafkaUser.class)
                .inNamespace("kafka").withName("tls-user").get();
        assertThat(created.getSpec().getAuthentication().getType()).isEqualTo("tls");
    }

    @Test
    void executeShouldCreateUserWithQuotas() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "quota-user");
        args.put("namespace", "kafka");
        args.put("kafkaCluster", "my-cluster");
        args.put("producerByteRate", 1048576);
        args.put("consumerByteRate", 2097152);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("create_user", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Producer Byte Rate: 1048576");
        assertThat(content).contains("Consumer Byte Rate: 2097152");

        KafkaUser created = client.resources(KafkaUser.class)
                .inNamespace("kafka").withName("quota-user").get();
        assertThat(created.getSpec().getQuotas().getProducerByteRate()).isEqualTo(1048576);
        assertThat(created.getSpec().getQuotas().getConsumerByteRate()).isEqualTo(2097152);
    }

    @Test
    void executeShouldFailWhenUserAlreadyExists() {
        KafkaUser existing = new KafkaUserBuilder()
                .withNewMetadata()
                    .withName("existing-user")
                    .withNamespace("kafka")
                    .addToLabels(StrimziLabels.CLUSTER, "my-cluster")
                .endMetadata()
                .withNewSpec()
                    .withAuthentication(new KafkaUserScramSha512ClientAuthentication())
                .endSpec()
                .build();
        client.resources(KafkaUser.class).inNamespace("kafka").resource(existing).create();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "existing-user");
        args.put("namespace", "kafka");
        args.put("kafkaCluster", "my-cluster");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("create_user", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("already exists");
    }
}
