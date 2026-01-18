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
class ListCertificatesToolTest {

    KubernetesClient client;

    private ListCertificatesTool tool;

    @BeforeEach
    void setUp() {
        tool = new ListCertificatesTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("list_certificates");
    }

    @Test
    void executeShouldListCertificatesForKafkaCluster() {
        createKafka("my-cluster", "kafka");
        createCertSecret("my-cluster-cluster-ca-cert", "kafka", "my-cluster");
        createCertSecret("my-cluster-clients-ca-cert", "kafka", "my-cluster");

        Map<String, Object> args = new HashMap<>();
        args.put("kafkaCluster", "my-cluster");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_certificates", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Certificates for Kafka Cluster: kafka/my-cluster");
        assertThat(content).contains("CLUSTER CA CERTIFICATE");
        assertThat(content).contains("my-cluster-cluster-ca-cert");
        assertThat(content).contains("CLIENTS CA CERTIFICATE");
        assertThat(content).contains("my-cluster-clients-ca-cert");
    }

    @Test
    void executeShouldFailWhenKafkaClusterNotFound() {
        Map<String, Object> args = new HashMap<>();
        args.put("kafkaCluster", "non-existent");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_certificates", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }

    @Test
    void executeShouldHandleMissingSecrets() {
        createKafka("my-cluster", "kafka");
        // Don't create any secrets

        Map<String, Object> args = new HashMap<>();
        args.put("kafkaCluster", "my-cluster");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_certificates", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Not found");
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

    private void createCertSecret(String name, String namespace, String clusterLabel) {
        Map<String, String> data = new HashMap<>();
        data.put("ca.crt", "dGVzdC1jZXJ0aWZpY2F0ZQ=="); // base64 encoded test data

        client.secrets().inNamespace(namespace).resource(
            new SecretBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels("strimzi.io/cluster", clusterLabel)
                    .addToAnnotations("strimzi.io/ca-cert-generation", "0")
                .endMetadata()
                .withData(data)
                .build()
        ).create();
    }
}
