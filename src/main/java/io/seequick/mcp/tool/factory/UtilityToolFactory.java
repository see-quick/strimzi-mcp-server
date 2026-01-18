package io.seequick.mcp.tool.factory;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.seequick.mcp.tool.StrimziTool;
import io.seequick.mcp.tool.utility.ExportResourceYamlTool;
import io.seequick.mcp.tool.utility.GetStrimziVersionTool;
import io.seequick.mcp.tool.utility.ListAllResourcesTool;

import java.util.List;

/**
 * Factory for utility tools.
 */
public class UtilityToolFactory implements ToolFactory {

    @Override
    public List<StrimziTool> createTools(KubernetesClient client) {
        return List.of(
                new ExportResourceYamlTool(client),
                new GetStrimziVersionTool(client),
                new ListAllResourcesTool(client)
        );
    }
}
