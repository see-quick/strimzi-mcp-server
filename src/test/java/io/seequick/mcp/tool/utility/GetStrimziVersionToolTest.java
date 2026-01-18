package io.seequick.mcp.tool.utility;

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
class GetStrimziVersionToolTest {

    KubernetesClient client;

    private GetStrimziVersionTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetStrimziVersionTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("get_strimzi_version");
    }

    @Test
    void executeShouldReturnVersionInfo() {
        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_strimzi_version", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Strimzi Version Information");
        assertThat(content).contains("CLUSTER OPERATOR");
        assertThat(content).contains("KAFKA CLUSTERS");
    }

    @Test
    void executeShouldShowKafkaClusters() {
        createKafka("cluster-1", "kafka", "3.6.0");
        createKafka("cluster-2", "other-ns", "3.5.0");

        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_strimzi_version", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("kafka/cluster-1");
        assertThat(content).contains("other-ns/cluster-2");
        assertThat(content).contains("3.6.0");
        assertThat(content).contains("3.5.0");
    }

    @Test
    void executeShouldHandleNoKafkaClusters() {
        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_strimzi_version", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("No Kafka clusters found");
    }

    @Test
    void executeShouldSearchSpecificNamespace() {
        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "custom-ns");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_strimzi_version", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Strimzi Version Information");
    }

    private void createKafka(String name, String namespace, String version) {
        Kafka kafka = new KafkaBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withNewKafka()
                        .withReplicas(3)
                        .withVersion(version)
                    .endKafka()
                .endSpec()
                .build();
        client.resources(Kafka.class).inNamespace(namespace).resource(kafka).create();
    }
}