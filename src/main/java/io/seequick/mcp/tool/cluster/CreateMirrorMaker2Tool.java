package io.seequick.mcp.tool.cluster;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.strimzi.api.kafka.model.mirrormaker2.KafkaMirrorMaker2;
import io.strimzi.api.kafka.model.mirrormaker2.KafkaMirrorMaker2Builder;
import io.strimzi.api.kafka.model.mirrormaker2.KafkaMirrorMaker2List;
import io.seequick.mcp.tool.AbstractStrimziTool;

/**
 * Tool to create a new KafkaMirrorMaker2 resource for cross-cluster replication.
 */
public class CreateMirrorMaker2Tool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the KafkaMirrorMaker2 resource to create"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace to create the MirrorMaker2 in"
                    },
                    "replicas": {
                        "type": "integer",
                        "description": "Number of replicas (default: 1)"
                    },
                    "connectCluster": {
                        "type": "string",
                        "description": "Alias of the Kafka Connect cluster to use (should match one of the clusters)"
                    },
                    "sourceClusterAlias": {
                        "type": "string",
                        "description": "Alias for the source Kafka cluster"
                    },
                    "sourceBootstrapServers": {
                        "type": "string",
                        "description": "Bootstrap servers for the source cluster"
                    },
                    "targetClusterAlias": {
                        "type": "string",
                        "description": "Alias for the target Kafka cluster"
                    },
                    "targetBootstrapServers": {
                        "type": "string",
                        "description": "Bootstrap servers for the target cluster"
                    },
                    "topicsPattern": {
                        "type": "string",
                        "description": "Regex pattern for topics to replicate (default: .*)"
                    },
                    "groupsPattern": {
                        "type": "string",
                        "description": "Regex pattern for consumer groups to replicate (default: .*)"
                    }
                },
                "required": ["name", "namespace", "sourceClusterAlias", "sourceBootstrapServers", "targetClusterAlias", "targetBootstrapServers"]
            }
            """;

    public CreateMirrorMaker2Tool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "create_mirrormaker2";
    }

    @Override
    protected String getDescription() {
        return "Create a new KafkaMirrorMaker2 resource for cross-cluster replication";
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
            int replicas = getIntArg(args, "replicas", 1);
            String connectCluster = getStringArg(args, "connectCluster");
            String sourceAlias = getStringArg(args, "sourceClusterAlias");
            String sourceBootstrap = getStringArg(args, "sourceBootstrapServers");
            String targetAlias = getStringArg(args, "targetClusterAlias");
            String targetBootstrap = getStringArg(args, "targetBootstrapServers");
            String topicsPattern = getStringArg(args, "topicsPattern");
            String groupsPattern = getStringArg(args, "groupsPattern");

            if (topicsPattern == null) {
                topicsPattern = ".*";
            }
            if (groupsPattern == null) {
                groupsPattern = ".*";
            }
            if (connectCluster == null) {
                connectCluster = targetAlias;
            }

            // Check if already exists
            KafkaMirrorMaker2 existing = kubernetesClient.resources(KafkaMirrorMaker2.class, KafkaMirrorMaker2List.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (existing != null) {
                return error("KafkaMirrorMaker2 already exists: " + namespace + "/" + name);
            }

            // Build the MirrorMaker2
            KafkaMirrorMaker2 mm2 = new KafkaMirrorMaker2Builder()
                    .withNewMetadata()
                        .withName(name)
                        .withNamespace(namespace)
                    .endMetadata()
                    .withNewSpec()
                        .withReplicas(replicas)
                        .withConnectCluster(connectCluster)
                        .addNewCluster()
                            .withAlias(sourceAlias)
                            .withBootstrapServers(sourceBootstrap)
                        .endCluster()
                        .addNewCluster()
                            .withAlias(targetAlias)
                            .withBootstrapServers(targetBootstrap)
                        .endCluster()
                        .addNewMirror()
                            .withSourceCluster(sourceAlias)
                            .withTargetCluster(targetAlias)
                            .withNewSourceConnector()
                                .withTasksMax(2)
                            .endSourceConnector()
                            .withNewCheckpointConnector()
                                .withTasksMax(1)
                            .endCheckpointConnector()
                            .withTopicsPattern(topicsPattern)
                            .withGroupsPattern(groupsPattern)
                        .endMirror()
                    .endSpec()
                    .build();

            kubernetesClient.resources(KafkaMirrorMaker2.class, KafkaMirrorMaker2List.class)
                    .inNamespace(namespace)
                    .resource(mm2)
                    .create();

            StringBuilder result = new StringBuilder();
            result.append("Created KafkaMirrorMaker2: ").append(namespace).append("/").append(name).append("\n\n");
            result.append("Configuration:\n");
            result.append("  Replicas: ").append(replicas).append("\n");
            result.append("  Source: ").append(sourceAlias).append(" (").append(sourceBootstrap).append(")\n");
            result.append("  Target: ").append(targetAlias).append(" (").append(targetBootstrap).append(")\n");
            result.append("  Topics Pattern: ").append(topicsPattern).append("\n");
            result.append("  Groups Pattern: ").append(groupsPattern).append("\n");
            result.append("\nMirrorMaker2 will start replicating topics matching the pattern.\n");
            result.append("Use describe_mirrormaker2 to check status.");

            return success(result.toString());
        } catch (Exception e) {
            return error("Error creating MirrorMaker2: " + e.getMessage());
        }
    }
}
