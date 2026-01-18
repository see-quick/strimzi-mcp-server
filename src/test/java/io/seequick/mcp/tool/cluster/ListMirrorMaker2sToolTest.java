package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.strimzi.api.kafka.model.mirrormaker2.KafkaMirrorMaker2;
import io.strimzi.api.kafka.model.mirrormaker2.KafkaMirrorMaker2Builder;
import io.strimzi.api.kafka.model.mirrormaker2.KafkaMirrorMaker2ClusterSpecBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class ListMirrorMaker2sToolTest {

    KubernetesClient client;

    private ListMirrorMaker2sTool tool;

    @BeforeEach
    void setUp() {
        tool = new ListMirrorMaker2sTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("list_mirrormaker2s");
    }

    @Test
    void executeShouldListMirrorMaker2sInNamespace() {
        createMirrorMaker2("mm2-1", "kafka");
        createMirrorMaker2("mm2-2", "kafka");
        createMirrorMaker2("mm2-3", "other-ns");

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "kafka");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_mirrormaker2s", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 KafkaMirrorMaker2");
        assertThat(content).contains("mm2-1");
        assertThat(content).contains("mm2-2");
        assertThat(content).doesNotContain("mm2-3");
    }

    @Test
    void executeShouldListFromAllNamespacesWhenNotSpecified() {
        createMirrorMaker2("mm2-ns1", "ns1");
        createMirrorMaker2("mm2-ns2", "ns2");

        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_mirrormaker2s", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 2 KafkaMirrorMaker2");
        assertThat(content).contains("mm2-ns1");
        assertThat(content).contains("mm2-ns2");
    }

    @Test
    void executeShouldReturnEmptyListWhenNoMirrorMaker2s() {
        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "empty-ns");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_mirrormaker2s", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Found 0 KafkaMirrorMaker2");
    }

    private void createMirrorMaker2(String name, String namespace) {
        KafkaMirrorMaker2 mm2 = new KafkaMirrorMaker2Builder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withReplicas(1)
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
                .endSpec()
                .build();
        client.resources(KafkaMirrorMaker2.class).inNamespace(namespace).resource(mm2).create();
    }
}
