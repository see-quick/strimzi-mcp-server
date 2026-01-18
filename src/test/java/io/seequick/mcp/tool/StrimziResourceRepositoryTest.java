package io.seequick.mcp.tool;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.strimzi.api.kafka.model.topic.KafkaTopic;
import io.strimzi.api.kafka.model.topic.KafkaTopicBuilder;
import io.strimzi.api.kafka.model.topic.KafkaTopicList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class StrimziResourceRepositoryTest {

    KubernetesClient client;

    private StrimziResourceRepository<KafkaTopic, KafkaTopicList> repository;

    @BeforeEach
    void setUp() {
        repository = new StrimziResourceRepository<>(client, KafkaTopic.class, KafkaTopicList.class);
    }

    @Test
    void createShouldCreateResource() {
        KafkaTopic topic = new KafkaTopicBuilder()
                .withNewMetadata()
                    .withName("test-topic")
                    .withNamespace("kafka")
                    .addToLabels(StrimziLabels.CLUSTER, "my-cluster")
                .endMetadata()
                .withNewSpec()
                    .withPartitions(3)
                    .withReplicas(2)
                .endSpec()
                .build();

        KafkaTopic created = repository.create("kafka", topic);

        assertThat(created).isNotNull();
        assertThat(created.getMetadata().getName()).isEqualTo("test-topic");
    }

    @Test
    void getShouldRetrieveResource() {
        KafkaTopic topic = new KafkaTopicBuilder()
                .withNewMetadata()
                    .withName("existing-topic")
                    .withNamespace("kafka")
                .endMetadata()
                .withNewSpec()
                    .withPartitions(1)
                    .withReplicas(1)
                .endSpec()
                .build();
        repository.create("kafka", topic);

        KafkaTopic retrieved = repository.get("kafka", "existing-topic");

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getMetadata().getName()).isEqualTo("existing-topic");
    }

    @Test
    void getShouldReturnNullWhenNotFound() {
        KafkaTopic retrieved = repository.get("kafka", "non-existent");

        assertThat(retrieved).isNull();
    }

    @Test
    void existsShouldReturnTrueWhenResourceExists() {
        KafkaTopic topic = new KafkaTopicBuilder()
                .withNewMetadata()
                    .withName("check-topic")
                    .withNamespace("kafka")
                .endMetadata()
                .withNewSpec()
                    .withPartitions(1)
                    .withReplicas(1)
                .endSpec()
                .build();
        repository.create("kafka", topic);

        boolean exists = repository.exists("kafka", "check-topic");

        assertThat(exists).isTrue();
    }

    @Test
    void existsShouldReturnFalseWhenResourceDoesNotExist() {
        boolean exists = repository.exists("kafka", "missing-topic");

        assertThat(exists).isFalse();
    }

    @Test
    void listShouldListAllResourcesInNamespace() {
        createTopic("topic-1", "kafka", "cluster-a");
        createTopic("topic-2", "kafka", "cluster-a");
        createTopic("topic-3", "other-ns", "cluster-b");

        KafkaTopicList list = repository.list("kafka", null);

        assertThat(list.getItems()).hasSize(2);
    }

    @Test
    void listShouldFilterByClusterLabel() {
        createTopic("topic-1", "kafka", "cluster-a");
        createTopic("topic-2", "kafka", "cluster-b");
        createTopic("topic-3", "kafka", "cluster-a");

        KafkaTopicList list = repository.list("kafka", "cluster-a");

        assertThat(list.getItems()).hasSize(2);
        assertThat(list.getItems())
                .allMatch(t -> "cluster-a".equals(t.getMetadata().getLabels().get(StrimziLabels.CLUSTER)));
    }

    @Test
    void listShouldListFromAllNamespacesWhenNamespaceIsNull() {
        createTopic("topic-1", "ns1", "cluster-a");
        createTopic("topic-2", "ns2", "cluster-a");
        createTopic("topic-3", "ns3", "cluster-b");

        KafkaTopicList list = repository.list(null, null);

        assertThat(list.getItems()).hasSize(3);
    }

    @Test
    void deleteShouldRemoveResource() {
        createTopic("to-delete", "kafka", "my-cluster");
        assertThat(repository.exists("kafka", "to-delete")).isTrue();

        repository.delete("kafka", "to-delete");

        assertThat(repository.exists("kafka", "to-delete")).isFalse();
    }

    private void createTopic(String name, String namespace, String clusterLabel) {
        KafkaTopic topic = new KafkaTopicBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(StrimziLabels.CLUSTER, clusterLabel)
                .endMetadata()
                .withNewSpec()
                    .withPartitions(1)
                    .withReplicas(1)
                .endSpec()
                .build();
        repository.create(namespace, topic);
    }
}
