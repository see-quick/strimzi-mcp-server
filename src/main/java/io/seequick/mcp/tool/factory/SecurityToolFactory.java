package io.seequick.mcp.tool.factory;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.seequick.mcp.tool.StrimziTool;
import io.seequick.mcp.tool.security.GetCertificateExpiryTool;
import io.seequick.mcp.tool.security.ListCertificatesTool;
import io.seequick.mcp.tool.security.RotateUserCredentialsTool;

import java.util.List;

/**
 * Factory for security-related tools.
 */
public class SecurityToolFactory implements ToolFactory {

    @Override
    public List<StrimziTool> createTools(KubernetesClient client) {
        return List.of(
                new RotateUserCredentialsTool(client),
                new ListCertificatesTool(client),
                new GetCertificateExpiryTool(client)
        );
    }
}
