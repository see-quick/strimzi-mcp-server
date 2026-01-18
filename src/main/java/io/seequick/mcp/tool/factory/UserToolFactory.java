package io.seequick.mcp.tool.factory;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.seequick.mcp.tool.StrimziTool;
import io.seequick.mcp.tool.user.CreateUserTool;
import io.seequick.mcp.tool.user.DeleteUserTool;
import io.seequick.mcp.tool.user.DescribeUserTool;
import io.seequick.mcp.tool.user.GetUserCredentialsTool;
import io.seequick.mcp.tool.user.GetUserOperatorStatusTool;
import io.seequick.mcp.tool.user.ListUserAclsTool;
import io.seequick.mcp.tool.user.ListUsersTool;
import io.seequick.mcp.tool.user.UpdateUserAclsTool;
import io.seequick.mcp.tool.user.UpdateUserQuotasTool;

import java.util.List;

/**
 * Factory for User Operator-related tools.
 */
public class UserToolFactory implements ToolFactory {

    @Override
    public List<StrimziTool> createTools(KubernetesClient client) {
        return List.of(
                new ListUsersTool(client),
                new DescribeUserTool(client),
                new CreateUserTool(client),
                new DeleteUserTool(client),
                new GetUserCredentialsTool(client),
                new GetUserOperatorStatusTool(client),
                new UpdateUserAclsTool(client),
                new UpdateUserQuotasTool(client),
                new ListUserAclsTool(client)
        );
    }
}
