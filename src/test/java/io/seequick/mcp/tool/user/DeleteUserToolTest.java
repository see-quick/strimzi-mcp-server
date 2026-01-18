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
class DeleteUserToolTest {

    KubernetesClient client;

    private DeleteUserTool tool;

    @BeforeEach
    void setUp() {
        tool = new DeleteUserTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("delete_user");
    }

    @Test
    void executeShouldDeleteExistingUser() {
        createUser("user-to-delete", "kafka", "my-cluster");

        assertThat(client.resources(KafkaUser.class)
                .inNamespace("kafka").withName("user-to-delete").get()).isNotNull();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "user-to-delete");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("delete_user", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Deleted KafkaUser: kafka/user-to-delete");

        assertThat(client.resources(KafkaUser.class)
                .inNamespace("kafka").withName("user-to-delete").get()).isNull();
    }

    @Test
    void executeShouldFailWhenUserDoesNotExist() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "non-existent-user");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("delete_user", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }

    private void createUser(String name, String namespace, String cluster) {
        KafkaUser user = new KafkaUserBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(StrimziLabels.CLUSTER, cluster)
                .endMetadata()
                .withNewSpec()
                    .withAuthentication(new KafkaUserScramSha512ClientAuthentication())
                .endSpec()
                .build();
        client.resources(KafkaUser.class).inNamespace(namespace).resource(user).create();
    }
}
