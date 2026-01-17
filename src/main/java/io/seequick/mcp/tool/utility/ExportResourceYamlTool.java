package io.seequick.mcp.tool.utility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.kafka.Kafka;
import io.strimzi.api.kafka.model.kafka.KafkaList;
import io.strimzi.api.kafka.model.topic.KafkaTopic;
import io.strimzi.api.kafka.model.topic.KafkaTopicList;
import io.strimzi.api.kafka.model.user.KafkaUser;
import io.strimzi.api.kafka.model.user.KafkaUserList;
import io.strimzi.api.kafka.model.connect.KafkaConnect;
import io.strimzi.api.kafka.model.connect.KafkaConnectList;
import io.strimzi.api.kafka.model.connector.KafkaConnector;
import io.strimzi.api.kafka.model.connector.KafkaConnectorList;
import io.strimzi.api.kafka.model.nodepool.KafkaNodePool;
import io.strimzi.api.kafka.model.nodepool.KafkaNodePoolList;
import io.strimzi.api.kafka.model.mirrormaker2.KafkaMirrorMaker2;
import io.strimzi.api.kafka.model.mirrormaker2.KafkaMirrorMaker2List;
import io.strimzi.api.kafka.model.bridge.KafkaBridge;
import io.strimzi.api.kafka.model.bridge.KafkaBridgeList;
import io.seequick.mcp.tool.AbstractStrimziTool;

/**
 * Tool to export a Strimzi resource as YAML.
 */
public class ExportResourceYamlTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "kind": {
                        "type": "string",
                        "enum": ["Kafka", "KafkaTopic", "KafkaUser", "KafkaConnect", "KafkaConnector", "KafkaNodePool", "KafkaMirrorMaker2", "KafkaBridge"],
                        "description": "Kind of the resource to export"
                    },
                    "name": {
                        "type": "string",
                        "description": "Name of the resource"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the resource"
                    },
                    "includeStatus": {
                        "type": "boolean",
                        "description": "Include status in output (default: false)"
                    }
                },
                "required": ["kind", "name", "namespace"]
            }
            """;

    private static final ObjectMapper YAML_MAPPER;

    static {
        YAMLFactory factory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        YAML_MAPPER = new ObjectMapper(factory);
    }

    public ExportResourceYamlTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "export_resource_yaml";
    }

    @Override
    protected String getDescription() {
        return "Export a Strimzi resource as YAML (useful for backup, migration, or templating)";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    protected CallToolResult execute(McpSchema.CallToolRequest args) {
        try {
            String kind = getStringArg(args, "kind");
            String name = getStringArg(args, "name");
            String namespace = getStringArg(args, "namespace");
            Boolean includeStatus = args.arguments().get("includeStatus") != null ?
                    (Boolean) args.arguments().get("includeStatus") : false;

            HasMetadata resource = getResource(kind, name, namespace);

            if (resource == null) {
                return error(kind + " not found: " + namespace + "/" + name);
            }

            // Clean up metadata for export
            if (!Boolean.TRUE.equals(includeStatus)) {
                // Remove status by converting to map and removing status key
                var resourceMap = YAML_MAPPER.convertValue(resource, java.util.Map.class);
                resourceMap.remove("status");

                // Clean up metadata
                if (resourceMap.get("metadata") instanceof java.util.Map metadata) {
                    metadata.remove("resourceVersion");
                    metadata.remove("uid");
                    metadata.remove("creationTimestamp");
                    metadata.remove("generation");
                    metadata.remove("managedFields");

                    // Remove strimzi internal annotations
                    if (metadata.get("annotations") instanceof java.util.Map annotations) {
                        annotations.keySet().removeIf(k ->
                                k.toString().startsWith("strimzi.io/") &&
                                (k.toString().contains("generation") || k.toString().contains("last")));
                    }
                }

                String yaml = YAML_MAPPER.writeValueAsString(resourceMap);
                return success("# " + kind + ": " + namespace + "/" + name + "\n" + yaml);
            } else {
                String yaml = YAML_MAPPER.writeValueAsString(resource);
                return success("# " + kind + ": " + namespace + "/" + name + " (with status)\n" + yaml);
            }

        } catch (Exception e) {
            return error("Error exporting resource: " + e.getMessage());
        }
    }

    private HasMetadata getResource(String kind, String name, String namespace) {
        return switch (kind) {
            case "Kafka" -> kubernetesClient.resources(Kafka.class, KafkaList.class)
                    .inNamespace(namespace).withName(name).get();
            case "KafkaTopic" -> kubernetesClient.resources(KafkaTopic.class, KafkaTopicList.class)
                    .inNamespace(namespace).withName(name).get();
            case "KafkaUser" -> kubernetesClient.resources(KafkaUser.class, KafkaUserList.class)
                    .inNamespace(namespace).withName(name).get();
            case "KafkaConnect" -> kubernetesClient.resources(KafkaConnect.class, KafkaConnectList.class)
                    .inNamespace(namespace).withName(name).get();
            case "KafkaConnector" -> kubernetesClient.resources(KafkaConnector.class, KafkaConnectorList.class)
                    .inNamespace(namespace).withName(name).get();
            case "KafkaNodePool" -> kubernetesClient.resources(KafkaNodePool.class, KafkaNodePoolList.class)
                    .inNamespace(namespace).withName(name).get();
            case "KafkaMirrorMaker2" -> kubernetesClient.resources(KafkaMirrorMaker2.class, KafkaMirrorMaker2List.class)
                    .inNamespace(namespace).withName(name).get();
            case "KafkaBridge" -> kubernetesClient.resources(KafkaBridge.class, KafkaBridgeList.class)
                    .inNamespace(namespace).withName(name).get();
            default -> null;
        };
    }
}
