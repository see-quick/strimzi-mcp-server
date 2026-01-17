package io.seequick.mcp.tool.utility;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.kafka.Kafka;
import io.strimzi.api.kafka.model.kafka.KafkaList;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tool to get Strimzi and Kafka versions in the cluster.
 */
public class GetStrimziVersionTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "namespace": {
                        "type": "string",
                        "description": "Namespace where Cluster Operator is deployed (default: searches common namespaces)"
                    }
                }
            }
            """;

    public GetStrimziVersionTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "get_strimzi_version";
    }

    @Override
    protected String getDescription() {
        return "Get installed Strimzi operator version and Kafka versions in use";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    protected CallToolResult execute(McpSchema.CallToolRequest args) {
        try {
            String namespace = getStringArg(args, "namespace");

            StringBuilder result = new StringBuilder();
            result.append("Strimzi Version Information\n");
            result.append("═".repeat(60)).append("\n\n");

            // Find Cluster Operator
            Deployment coDeployment = null;
            String[] searchNamespaces = namespace != null ?
                    new String[]{namespace} :
                    new String[]{"strimzi", "strimzi-system", "kafka", "default"};

            for (String ns : searchNamespaces) {
                try {
                    Deployment dep = kubernetesClient.apps().deployments()
                            .inNamespace(ns)
                            .withName("strimzi-cluster-operator")
                            .get();
                    if (dep != null) {
                        coDeployment = dep;
                        namespace = ns;
                        break;
                    }
                } catch (Exception ignored) {
                    // Continue searching
                }
            }

            result.append("CLUSTER OPERATOR\n");
            result.append("─".repeat(40)).append("\n");

            if (coDeployment != null) {
                result.append("  Namespace: ").append(namespace).append("\n");

                // Get image version
                String image = coDeployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
                result.append("  Image: ").append(image).append("\n");

                // Extract version from image
                Pattern versionPattern = Pattern.compile(":([0-9]+\\.[0-9]+\\.[0-9]+)");
                Matcher matcher = versionPattern.matcher(image);
                if (matcher.find()) {
                    result.append("  Version: ").append(matcher.group(1)).append("\n");
                }

                // Check operator pod status
                List<Pod> coPods = kubernetesClient.pods()
                        .inNamespace(namespace)
                        .withLabel("name", "strimzi-cluster-operator")
                        .list()
                        .getItems();

                if (!coPods.isEmpty()) {
                    Pod coPod = coPods.get(0);
                    result.append("  Status: ").append(coPod.getStatus().getPhase()).append("\n");
                }
            } else {
                result.append("  Not found in searched namespaces\n");
            }
            result.append("\n");

            // List Kafka clusters and versions
            result.append("KAFKA CLUSTERS\n");
            result.append("─".repeat(40)).append("\n");

            List<Kafka> kafkas = kubernetesClient.resources(Kafka.class, KafkaList.class)
                    .inAnyNamespace()
                    .list()
                    .getItems();

            if (kafkas.isEmpty()) {
                result.append("  No Kafka clusters found\n");
            } else {
                for (Kafka kafka : kafkas) {
                    String ns = kafka.getMetadata().getNamespace();
                    String name = kafka.getMetadata().getName();
                    result.append("  ").append(ns).append("/").append(name).append(":\n");

                    if (kafka.getSpec().getKafka() != null) {
                        String kafkaVersion = kafka.getSpec().getKafka().getVersion();
                        result.append("    Kafka Version: ").append(kafkaVersion != null ? kafkaVersion : "default").append("\n");
                    }

                    if (kafka.getStatus() != null) {
                        // Get operator version from status if available
                        if (kafka.getStatus().getOperatorLastSuccessfulVersion() != null) {
                            result.append("    Operator Version: ").append(kafka.getStatus().getOperatorLastSuccessfulVersion()).append("\n");
                        }
                        if (kafka.getStatus().getKafkaVersion() != null) {
                            result.append("    Running Kafka: ").append(kafka.getStatus().getKafkaVersion()).append("\n");
                        }
                    }
                }
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error getting Strimzi version: " + e.getMessage());
        }
    }
}
