package io.seequick.mcp.tool.utility;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.seequick.mcp.tool.StrimziLabels;
import io.strimzi.api.kafka.model.kafka.Kafka;
import io.strimzi.api.kafka.model.kafka.KafkaBuilder;
import io.strimzi.api.kafka.model.topic.KafkaTopic;
import io.strimzi.api.kafka.model.topic.KafkaTopicBuilder;
import io.strimzi.api.kafka.model.user.KafkaUser;
import io.strimzi.api.kafka.model.user.KafkaUserBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class ListAllResourcesToolTest {

    KubernetesClient client;

    private ListAllResourcesTool tool;

    @BeforeEach
    void setUp() {
        tool = new ListAllResourcesTool(client);
    }

    @Test
    void getNameShouldReturnCorrectName() {
        var spec = tool.getSpecification();
        assertThat(spec.tool().name()).isEqualTo("list_all_resources");
    }

    @Test
    void executeShouldListAllResources() {
        createKafka("my-cluster", "kafka");
        createTopic("my-topic", "kafka", "my-cluster");
        createUser("my-user", "kafka", "my-cluster");

        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_all_resources", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Strimzi Resources Summary");
        assertThat(content).contains("KAFKA CLUSTERS (1)");
        assertThat(content).contains("TOPICS (1)");
        assertThat(content).contains("USERS (1)");
        assertThat(content).contains("TOTAL: 3 Strimzi resources");
    }

    @Test
    void executeShouldFilterByNamespace() {
        createKafka("cluster-1", "ns1");
        createKafka("cluster-2", "ns2");
        createTopic("topic-1", "ns1", "cluster-1");
        createTopic("topic-2", "ns2", "cluster-2");

        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "ns1");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_all_resources", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Namespace: ns1");
        assertThat(content).contains("KAFKA CLUSTERS (1)");
        assertThat(content).contains("TOPICS (1)");
        assertThat(content).contains("cluster-1");
        assertThat(content).doesNotContain("cluster-2");
    }

    @Test
    void executeShouldShowEmptyCluster() {
        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "empty-ns");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("list_all_resources", args);

        CallToolResult result = tool.getSpecification().callHandler().apply(null, request);

        assertThat(result.isError()).isFalse();
        String content = ((TextContent) result.content().get(0)).text();
        assertThat(content).contains("Strimzi Resources Summary");
        assertThat(content).contains("KAFKA CLUSTERS (0)");
        assertThat(content).contains("TOTAL: 0 Strimzi resources");
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

    private void createTopic(String name, String namespace, String cluster) {
        KafkaTopic topic = new KafkaTopicBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(StrimziLabels.CLUSTER, cluster)
                .endMetadata()
                .withNewSpec()
                    .withPartitions(3)
                    .withReplicas(3)
                .endSpec()
                .build();
        client.resources(KafkaTopic.class).inNamespace(namespace).resource(topic).create();
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
}
