package io.seequick.mcp.tool.observability;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.seequick.mcp.tool.AbstractStrimziTool;

import java.util.Map;

/**
 * Tool to get detailed information about a Kafka/Strimzi pod.
 */
public class DescribeKafkaPodTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": {
                        "type": "string",
                        "description": "Name of the pod"
                    },
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace of the pod"
                    }
                },
                "required": ["name", "namespace"]
            }
            """;

    public DescribeKafkaPodTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "describe_kafka_pod";
    }

    @Override
    protected String getDescription() {
        return "Get detailed information about a Kafka/Strimzi pod including resources, status, and events";
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

            Pod pod = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (pod == null) {
                return error("Pod not found: " + namespace + "/" + name);
            }

            StringBuilder result = new StringBuilder();
            result.append("Pod: ").append(namespace).append("/").append(name).append("\n");
            result.append("═".repeat(60)).append("\n\n");

            // Labels
            result.append("Labels:\n");
            Map<String, String> labels = pod.getMetadata().getLabels();
            if (labels != null) {
                labels.forEach((k, v) -> result.append("  ").append(k).append(": ").append(v).append("\n"));
            }
            result.append("\n");

            // Status
            var status = pod.getStatus();
            result.append("Status:\n");
            result.append("  Phase: ").append(status.getPhase()).append("\n");
            if (status.getReason() != null) {
                result.append("  Reason: ").append(status.getReason()).append("\n");
            }
            if (status.getMessage() != null) {
                result.append("  Message: ").append(status.getMessage()).append("\n");
            }
            result.append("  Pod IP: ").append(status.getPodIP()).append("\n");
            result.append("  Node: ").append(pod.getSpec().getNodeName()).append("\n");
            if (status.getStartTime() != null) {
                result.append("  Started: ").append(status.getStartTime()).append("\n");
            }
            result.append("\n");

            // Conditions
            if (status.getConditions() != null && !status.getConditions().isEmpty()) {
                result.append("Conditions:\n");
                for (var condition : status.getConditions()) {
                    result.append("  ").append(condition.getType()).append(": ")
                            .append(condition.getStatus());
                    if (condition.getReason() != null) {
                        result.append(" (").append(condition.getReason()).append(")");
                    }
                    result.append("\n");
                }
                result.append("\n");
            }

            // Containers
            result.append("Containers:\n");
            for (Container container : pod.getSpec().getContainers()) {
                result.append("  ").append(container.getName()).append(":\n");
                result.append("    Image: ").append(container.getImage()).append("\n");

                // Resources
                if (container.getResources() != null) {
                    var requests = container.getResources().getRequests();
                    var limits = container.getResources().getLimits();

                    if (requests != null && !requests.isEmpty()) {
                        result.append("    Requests:\n");
                        for (Map.Entry<String, Quantity> entry : requests.entrySet()) {
                            result.append("      ").append(entry.getKey()).append(": ")
                                    .append(entry.getValue().toString()).append("\n");
                        }
                    }
                    if (limits != null && !limits.isEmpty()) {
                        result.append("    Limits:\n");
                        for (Map.Entry<String, Quantity> entry : limits.entrySet()) {
                            result.append("      ").append(entry.getKey()).append(": ")
                                    .append(entry.getValue().toString()).append("\n");
                        }
                    }
                }

                // Find container status
                ContainerStatus containerStatus = null;
                if (status.getContainerStatuses() != null) {
                    containerStatus = status.getContainerStatuses().stream()
                            .filter(cs -> cs.getName().equals(container.getName()))
                            .findFirst()
                            .orElse(null);
                }

                if (containerStatus != null) {
                    result.append("    Status:\n");
                    result.append("      Ready: ").append(containerStatus.getReady()).append("\n");
                    result.append("      Restart Count: ").append(containerStatus.getRestartCount()).append("\n");

                    if (containerStatus.getState() != null) {
                        if (containerStatus.getState().getRunning() != null) {
                            result.append("      State: Running since ")
                                    .append(containerStatus.getState().getRunning().getStartedAt()).append("\n");
                        } else if (containerStatus.getState().getWaiting() != null) {
                            result.append("      State: Waiting - ")
                                    .append(containerStatus.getState().getWaiting().getReason()).append("\n");
                        } else if (containerStatus.getState().getTerminated() != null) {
                            result.append("      State: Terminated - ")
                                    .append(containerStatus.getState().getTerminated().getReason()).append("\n");
                        }
                    }

                    if (containerStatus.getLastState() != null && containerStatus.getLastState().getTerminated() != null) {
                        var lastTerm = containerStatus.getLastState().getTerminated();
                        result.append("      Last Termination:\n");
                        result.append("        Reason: ").append(lastTerm.getReason()).append("\n");
                        result.append("        Exit Code: ").append(lastTerm.getExitCode()).append("\n");
                        if (lastTerm.getFinishedAt() != null) {
                            result.append("        Finished: ").append(lastTerm.getFinishedAt()).append("\n");
                        }
                    }
                }
                result.append("\n");
            }

            // Volumes summary
            if (pod.getSpec().getVolumes() != null && !pod.getSpec().getVolumes().isEmpty()) {
                result.append("Volumes:\n");
                for (var volume : pod.getSpec().getVolumes()) {
                    result.append("  ").append(volume.getName());
                    if (volume.getPersistentVolumeClaim() != null) {
                        result.append(" (PVC: ").append(volume.getPersistentVolumeClaim().getClaimName()).append(")");
                    } else if (volume.getConfigMap() != null) {
                        result.append(" (ConfigMap: ").append(volume.getConfigMap().getName()).append(")");
                    } else if (volume.getSecret() != null) {
                        result.append(" (Secret: ").append(volume.getSecret().getSecretName()).append(")");
                    }
                    result.append("\n");
                }
            }

            return success(result.toString());
        } catch (Exception e) {
            return error("Error describing pod: " + e.getMessage());
        }
    }
}
