package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.strimzi.api.kafka.model.mirrormaker2.KafkaMirrorMaker2;
import io.strimzi.api.kafka.model.mirrormaker2.KafkaMirrorMaker2Builder;
import io.strimzi.api.kafka.model.mirrormaker2.KafkaMirrorMaker2ClusterSpecBuilder;
import io.strimzi.api.kafka.model.mirrormaker2.KafkaMirrorMaker2MirrorSpecBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class DescribeMirrorMaker2ToolTest {

    KubernetesClient client;

    private DescribeMirrorMaker2Tool tool;

    @BeforeEach
    void setUp() {
        tool = new DescribeMirrorMaker2Tool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("describe_mirrormaker2");
    }

    @Test
    void executeShouldDescribeMirrorMaker2() {
        createMirrorMaker2("my-mm2", "kafka", "source", "target");

        Map<String, Object> args = new HashMap<>();
        args.put("name", "my-mm2");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_mirrormaker2", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("KafkaMirrorMaker2: kafka/my-mm2");
        assertThat(content).contains("Replicas: 1");
        assertThat(content).contains("Clusters:");
        assertThat(content).contains("source");
        assertThat(content).contains("target");
    }

    @Test
    void executeShouldFailWhenMirrorMaker2NotFound() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", "non-existent");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_mirrormaker2", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isTrue();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("not found");
    }

    @Test
    void executeShouldShowMirrors() {
        KafkaMirrorMaker2 mm2 = new KafkaMirrorMaker2Builder()
                .withNewMetadata()
                    .withName("mm2-with-mirrors")
                    .withNamespace("kafka")
                .endMetadata()
                .withNewSpec()
                    .withReplicas(2)
                    .withConnectCluster("target")
                    .withClusters(List.of(
                        new KafkaMirrorMaker2ClusterSpecBuilder()
                            .withAlias("source")
                            .withBootstrapServers("source-kafka:9092")
                            .build(),
                        new KafkaMirrorMaker2ClusterSpecBuilder()
                            .withAlias("target")
                            .withBootstrapServers("target-kafka:9092")
                            .build()
                    ))
                    .withMirrors(List.of(
                        new KafkaMirrorMaker2MirrorSpecBuilder()
                            .withSourceCluster("source")
                            .withTargetCluster("target")
                            .withTopicsPattern(".*")
                            .build()
                    ))
                .endSpec()
                .build();
        client.resources(KafkaMirrorMaker2.class).inNamespace("kafka").resource(mm2).create();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "mm2-with-mirrors");
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("describe_mirrormaker2", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Mirrors:");
        assertThat(content).contains("source -> target");
        assertThat(content).contains("Topics Pattern:");
    }

    private void createMirrorMaker2(String name, String namespace, String sourceAlias, String targetAlias) {
        KafkaMirrorMaker2 mm2 = new KafkaMirrorMaker2Builder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withReplicas(1)
                    .withConnectCluster(targetAlias)
                    .withClusters(List.of(
                        new KafkaMirrorMaker2ClusterSpecBuilder()
                            .withAlias(sourceAlias)
                            .withBootstrapServers(sourceAlias + "-kafka:9092")
                            .build(),
                        new KafkaMirrorMaker2ClusterSpecBuilder()
                            .withAlias(targetAlias)
                            .withBootstrapServers(targetAlias + "-kafka:9092")
                            .build()
                    ))
                .endSpec()
                .build();
        client.resources(KafkaMirrorMaker2.class).inNamespace(namespace).resource(mm2).create();
    }
}
