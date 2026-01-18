package io.seequick.mcp.tool.kafka;

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
class ListKafkasToolTest {

    KubernetesClient client;

    private ListKafkasTool tool;

    @BeforeEach
    void setUp() {
        tool = new ListKafkasTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("list_kafkas");
    }

    @Test
    void executeShouldListKafkaClustersInNamespace() {
        createKafka("cluster-1", "kafka");
        createKafka("cluster-2", "kafka");
        createKafka("cluster-3", "other-ns");

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_kafkas", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 Kafka cluster(s)");
        assertThat(content).contains("cluster-1");
        assertThat(content).contains("cluster-2");
        assertThat(content).doesNotContain("cluster-3");
    }

    @Test
    void executeShouldListFromAllNamespacesWhenNotSpecified() {
        createKafka("cluster-ns1", "ns1");
        createKafka("cluster-ns2", "ns2");

        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_kafkas", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 Kafka cluster(s)");
        assertThat(content).contains("cluster-ns1");
        assertThat(content).contains("cluster-ns2");
    }

    @Test
    void executeShouldReturnEmptyListWhenNoClusters() {
        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "empty-ns");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_kafkas", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 0 Kafka cluster(s)");
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
