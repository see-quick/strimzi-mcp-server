package io.seequick.mcp.tool.kafka;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.kafka.Kafka;
import io.strimzi.api.kafka.model.kafka.KafkaList;
import io.seequick.mcp.tool.AbstractStrimziTool;

/**
 * Tool to list Strimzi Kafka clusters in the Kubernetes cluster.
 */
public class ListKafkasTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace to list Kafka clusters from. If not specified, lists from all namespaces."
                    }
                }
            }
            """;

    public ListKafkasTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "list_kafkas";
    }

    @Override
    protected String getDescription() {
        return "List Strimzi Kafka clusters in the Kubernetes cluster";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    protected CallToolResult execute(McpSchema.CallToolRequest args) {
        try {
            String namespace = getStringArg(args, "namespace");

            KafkaList kafkaList;
            if (namespace != null && !namespace.isEmpty()) {
                kafkaList = kubernetesClient.resources(Kafka.class, KafkaList.class)
                        .inNamespace(namespace)
                        .list();
            } else {
                kafkaList = kubernetesClient.resources(Kafka.class, KafkaList.class)
                        .inAnyNamespace()
                        .list();
            }

            StringBuilder result = new StringBuilder();
            result.append("Found ").append(kafkaList.getItems().size()).append(" Kafka cluster(s):\n\n");

            for (Kafka kafka : kafkaList.getItems()) {
                result.append("- ").append(kafka.getMetadata().getNamespace())
                        .append("/").append(kafka.getMetadata().getName());

                if (kafka.getStatus() != null) {
                    var conditions = kafka.getStatus().getConditions();
                    if (conditions != null && !conditions.isEmpty()) {
                        var readyCondition = conditions.stream()
                                .filter(c -> "Ready".equals(c.getType()))
                                .findFirst();
                        readyCondition.ifPresent(c ->
                                result.append(" [Ready: ").append(c.getStatus()).append("]")
                        );
                    }
                }
                result.append("\n");
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error listing Kafka clusters: " + e.getMessage());
        }
    }
}
