package io.seequick.mcp.tool.kafka;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.kafka.Kafka;
import io.strimzi.api.kafka.model.kafka.KafkaList;
import io.strimzi.api.kafka.model.podset.StrimziPodSet;
import io.strimzi.api.kafka.model.podset.StrimziPodSetList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.time.Instant;
import java.util.List;

/**
 * Tool to trigger a rolling restart of Kafka brokers via annotation.
 */
public class RestartKafkaBrokerTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the Kafka cluster"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the Kafka cluster"
                    },
                    "nodePool": {
                        "type": "string",
                        "description": "Optional: specific node pool to restart. If not specified, restarts all brokers"
                    },
                    "podName": {
                        "type": "string",
                        "description": "Optional: specific pod name to restart. If not specified, restarts all pods in the scope"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    private static final String RESTART_ANNOTATION = "strimzi.io/manual-rolling-update";

    public RestartKafkaBrokerTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "restart_kafka_broker";
    }

    @Override
    protected String getDescription() {
        return "Trigger a rolling restart of Kafka brokers via Strimzi annotation. Can restart all brokers, a specific node pool, or a single pod.";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    protected CallToolResult execute(McpSchema.CallToolRequest args) {
        try {
            String name = getStringArg(args, "name");
            String namespace = getStringArg(args, "namespace");
            String nodePool = getStringArg(args, "nodePool");
            String podName = getStringArg(args, "podName");

            // Verify Kafka cluster exists
            Kafka kafka = kubernetesClient.resources(Kafka.class, KafkaList.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (kafka == null) {
                return error("Kafka cluster not found: " + namespace + "/" + name);
            }

            StringBuilder result = new StringBuilder();
            String timestamp = Instant.now().toString();

            if (podName != null) {
                // Restart specific pod
                Pod pod = kubernetesClient.pods()
                        .inNamespace(namespace)
                        .withName(podName)
                        .get();

                if (pod == null) {
                    return error("Pod not found: " + namespace + "/" + podName);
                }

                kubernetesClient.pods()
                        .inNamespace(namespace)
                        .withName(podName)
                        .edit(p -> {
                            p.getMetadata().getAnnotations().put(RESTART_ANNOTATION, timestamp);
                            return p;
                        });

                result.append("Triggered restart for pod: ").append(podName).append("\n");
            } else if (nodePool != null) {
                // Restart specific node pool via StrimziPodSet
                String podSetName = name + "-" + nodePool;
                StrimziPodSet podSet = kubernetesClient.resources(StrimziPodSet.class, StrimziPodSetList.class)
                        .inNamespace(namespace)
                        .withName(podSetName)
                        .get();

                if (podSet == null) {
                    return error("StrimziPodSet not found: " + namespace + "/" + podSetName);
                }

                kubernetesClient.resources(StrimziPodSet.class, StrimziPodSetList.class)
                        .inNamespace(namespace)
                        .withName(podSetName)
                        .edit(ps -> {
                            if (ps.getMetadata().getAnnotations() == null) {
                                ps.getMetadata().setAnnotations(new java.util.HashMap<>());
                            }
                            ps.getMetadata().getAnnotations().put(RESTART_ANNOTATION, timestamp);
                            return ps;
                        });

                result.append("Triggered rolling restart for node pool: ").append(nodePool).append("\n");
            } else {
                // Restart all Kafka pods via Kafka resource annotation
                kubernetesClient.resources(Kafka.class, KafkaList.class)
                        .inNamespace(namespace)
                        .withName(name)
                        .edit(k -> {
                            if (k.getMetadata().getAnnotations() == null) {
                                k.getMetadata().setAnnotations(new java.util.HashMap<>());
                            }
                            k.getMetadata().getAnnotations().put(RESTART_ANNOTATION, timestamp);
                            return k;
                        });

                result.append("Triggered rolling restart for all Kafka brokers in cluster: ").append(name).append("\n");
            }

            result.append("\nThe Cluster Operator will perform the rolling restart. ");
            result.append("Use get_kafka_status to monitor progress.");

            return success(result.toString());
        } catch (Exception e) {
            return error("Error triggering restart: " + e.getMessage());
        }
    }
}
