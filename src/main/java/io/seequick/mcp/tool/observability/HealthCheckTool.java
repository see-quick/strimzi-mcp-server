package io.seequick.mcp.tool.observability;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.seequick.mcp.tool.AbstractStrimziTool;
import io.seequick.mcp.tool.observability.health.ConnectorHealthChecker;
import io.seequick.mcp.tool.observability.health.HealthCheckContext;
import io.seequick.mcp.tool.observability.health.HealthCheckResult;
import io.seequick.mcp.tool.observability.health.HealthChecker;
import io.seequick.mcp.tool.observability.health.KafkaHealthChecker;
import io.seequick.mcp.tool.observability.health.TopicHealthChecker;
import io.seequick.mcp.tool.observability.health.UserHealthChecker;

import java.util.List;

/**
 * Tool to perform a comprehensive health check of Strimzi resources.
 */
public class HealthCheckTool extends AbstractStrimziTool {

    private static final String SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "namespace": {
                        "type": "string",
                        "description": "Kubernetes namespace to check. If not specified, checks all namespaces."
                    },
                    "kafkaCluster": {
                        "type": "string",
                        "description": "Optional: specific Kafka cluster to check"
                    }
                }
            }
            """;

    private static final List<HealthChecker> CHECKERS = List.of(
            new KafkaHealthChecker(),
            new TopicHealthChecker(),
            new UserHealthChecker(),
            new ConnectorHealthChecker()
    );

    public HealthCheckTool(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    protected String getName() {
        return "health_check";
    }

    @Override
    protected String getDescription() {
        return "Perform a comprehensive health check of Strimzi resources and report any issues";
    }

    @Override
    protected JsonSchema getInputSchema() {
        return parseSchema(SCHEMA);
    }

    @Override
    protected CallToolResult execute(McpSchema.CallToolRequest args) {
        try {
            HealthCheckContext context = new HealthCheckContext(
                    kubernetesClient,
                    getStringArg(args, "namespace"),
                    getStringArg(args, "kafkaCluster")
            );
            HealthCheckResult result = new HealthCheckResult();

            for (HealthChecker checker : CHECKERS) {
                checker.check(context, result);
            }

            return success(result.format());
        } catch (Exception e) {
            return error("Error performing health check: " + e.getMessage());
        }
    }
}
