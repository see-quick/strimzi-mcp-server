package io.seequick.mcp.tool.security;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.strimzi.api.kafka.model.kafka.Kafka;
import io.strimzi.api.kafka.model.kafka.KafkaBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class GetCertificateExpiryToolTest {

    KubernetesClient client;

    private GetCertificateExpiryTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetCertificateExpiryTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("get_certificate_expiry");
    }

    @Test
    void executeShouldFailWhenKafkaNotFound() {
        Map<String, Object> args = new HashMap<>();
        args.put("kafkaCluster", "non-existent");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_certificate_expiry", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }

    @Test
    void executeShouldReturnReportWhenKafkaExists() {
        createKafka("my-cluster", "kafka");

        Map<String, Object> args = new HashMap<>();
        args.put("kafkaCluster", "my-cluster");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_certificate_expiry", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Certificate Expiry Report for: kafka/my-cluster");
        assertThat(content).contains("CLUSTER CA");
        assertThat(content).contains("CLIENTS CA");
    }

    @Test
    void executeShouldShowSecretNotFoundWhenMissingSecrets() {
        createKafka("no-secrets-cluster", "kafka");
        // Don't create the secrets

        Map<String, Object> args = new HashMap<>();
        args.put("kafkaCluster", "no-secrets-cluster");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_certificate_expiry", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Secret not found");
    }

    @Test
    void executeShouldAcceptCustomWarningDays() {
        createKafka("warn-cluster", "kafka");

        Map<String, Object> args = new HashMap<>();
        args.put("kafkaCluster", "warn-cluster");
        args.put("namespace", "kafka");
        args.put("warningDays", 60);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_certificate_expiry", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Certificate Expiry Report");
    }

    private void createKafka(String name, String namespace) {
        Kafka kafka = new KafkaBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withNewKafka()
                        .withReplicas(3)
                    .endKafka()
                .endSpec()
                .build();
        client.resources(Kafka.class).inNamespace(namespace).resource(kafka).create();
    }
}
