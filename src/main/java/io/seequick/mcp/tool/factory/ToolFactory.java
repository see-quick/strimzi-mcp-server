package io.seequick.mcp.tool.factory;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.seequick.mcp.tool.StrimziTool;

import java.util.List;

/**
 * Factory interface for creating groups of related Strimzi tools.
 */
public interface ToolFactory {

    /**
     * Creates all tools managed by this factory.
     *
     * @param client The Kubernetes client to use for tool operations
     * @return List of tools created by this factory
     */
    List<StrimziTool> createTools(KubernetesClient client);
}
