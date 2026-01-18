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
import io.strimzi.api.kafka.model.user.KafkaUserTlsClientAuthentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class ListUsersToolTest {

    KubernetesClient client;

    private ListUsersTool tool;

    @BeforeEach
    void setUp() {
        tool = new ListUsersTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("list_users");
    }

    @Test
    void executeShouldListUsersInNamespace() {
        createUser("user-1", "kafka", "my-cluster", "scram-sha-512");
        createUser("user-2", "kafka", "my-cluster", "tls");
        createUser("user-3", "other-ns", "other-cluster", "scram-sha-512");

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_users", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 KafkaUser(s)");
        assertThat(content).contains("user-1");
        assertThat(content).contains("user-2");
        assertThat(content).doesNotContain("user-3");
    }

    @Test
    void executeShouldFilterByKafkaCluster() {
        createUser("user-a", "kafka", "cluster-a", "scram-sha-512");
        createUser("user-b", "kafka", "cluster-b", "scram-sha-512");
        createUser("user-c", "kafka", "cluster-a", "tls");

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        args.put("kafkaCluster", "cluster-a");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_users", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 KafkaUser(s)");
        assertThat(content).contains("user-a");
        assertThat(content).contains("user-c");
        assertThat(content).doesNotContain("user-b");
    }

    @Test
    void executeShouldListFromAllNamespacesWhenNotSpecified() {
        createUser("user-ns1", "ns1", "cluster", "scram-sha-512");
        createUser("user-ns2", "ns2", "cluster", "scram-sha-512");

        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_users", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 KafkaUser(s)");
        assertThat(content).contains("user-ns1");
        assertThat(content).contains("user-ns2");
    }

    @Test
    void executeShouldDisplayAuthType() {
        createUser("scram-user", "kafka", "my-cluster", "scram-sha-512");
        createUser("tls-user", "kafka", "my-cluster", "tls");

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_users", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("[auth: scram-sha-512]");
        assertThat(content).contains("[auth: tls]");
    }

    @Test
    void executeShouldReturnEmptyListWhenNoUsers() {
        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "empty-ns");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_users", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 0 KafkaUser(s)");
    }

    private void createUser(String name, String namespace, String cluster, String authType) {
        var builder = new KafkaUserBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(StrimziLabels.CLUSTER, cluster)
                .endMetadata()
                .withNewSpec()
                .endSpec();

        if ("tls".equals(authType)) {
            builder.editSpec().withAuthentication(new KafkaUserTlsClientAuthentication()).endSpec();
        } else {
            builder.editSpec().withAuthentication(new KafkaUserScramSha512ClientAuthentication()).endSpec();
        }

        client.resources(KafkaUser.class).inNamespace(namespace).resource(builder.build()).create();
    }
}
