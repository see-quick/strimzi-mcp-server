package io.seequick.mcp.tool.user;

import io.fabric8.kubernetes.api.model.SecretBuilder;
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

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class GetUserCredentialsToolTest {

    KubernetesClient client;

    private GetUserCredentialsTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetUserCredentialsTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("get_user_credentials");
    }

    @Test
    void executeShouldReturnScramCredentials() {
        createUser("test-user", "kafka", "my-cluster");
        createScramSecret("test-user", "kafka", "mypassword123");

        Map<String, Object> args = new HashMap<>();
        args.put("name", "test-user");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_user_credentials", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Credentials for KafkaUser: kafka/test-user");
        assertThat(content).contains("Authentication: SCRAM-SHA-512");
        assertThat(content).contains("Username: test-user");
        assertThat(content).contains("Password: mypassword123");
    }

    @Test
    void executeShouldFailWhenUserNotFound() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "non-existent");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_user_credentials", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }

    @Test
    void executeShouldFailWhenSecretNotFound() {
        createUser("user-no-secret", "kafka", "my-cluster");
        // Don't create the secret

        Map<String, Object> args = new HashMap<>();
        args.put("name", "user-no-secret");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_user_credentials", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("secret not found");
    }

    @Test
    void executeShouldShowJaasConfig() {
        createUser("jaas-user", "kafka", "my-cluster");

        String jaasConfig = "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"jaas-user\" password=\"secret\";";
        Map<String, String> data = new HashMap<>();
        data.put("password", Base64.getEncoder().encodeToString("secret".getBytes()));
        data.put("sasl.jaas.config", Base64.getEncoder().encodeToString(jaasConfig.getBytes()));

        client.secrets().inNamespace("kafka").resource(
            new SecretBuilder()
                .withNewMetadata()
                    .withName("jaas-user")
                    .withNamespace("kafka")
                .endMetadata()
                .withData(data)
                .build()
        ).create();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "jaas-user");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_user_credentials", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("JAAS Config:");
        assertThat(content).contains("ScramLoginModule");
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

    private void createScramSecret(String name, String namespace, String password) {
        Map<String, String> data = new HashMap<>();
        data.put("password", Base64.getEncoder().encodeToString(password.getBytes()));

        client.secrets().inNamespace(namespace).resource(
            new SecretBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .withData(data)
                .build()
        ).create();
    }
}
