package io.seequick.mcp.tool;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class AbstractStrimziToolTest {

    @Mock
    private KubernetesClient kubernetesClient;

    private TestStrimziTool tool;

    @BeforeEach
    void setUp() {
        tool = new TestStrimziTool(kubernetesClient);
    }

    @Test
    void parseSchemaShouldParseValidJsonSchema() {
        String schemaJson = """
                {
                    "type": "object",
                    "properties": {
                        "namespace": {
                            "type": "string",
                            "description": "Kubernetes namespace"
                        },
                        "name": {
                            "type": "string",
                            "description": "Resource name"
                        }
                    },
                    "required": ["namespace", "name"]
                }
                """;

        JsonSchema schema = AbstractStrimziTool.parseSchema(schemaJson);

        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties()).containsKeys("namespace", "name");
        assertThat(schema.required()).containsExactly("namespace", "name");
    }

    @Test
    void parseSchemaShouldHandleSchemaWithoutRequired() {
        String schemaJson = """
                {
                    "type": "object",
                    "properties": {
                        "namespace": {
                            "type": "string"
                        }
                    }
                }
                """;

        JsonSchema schema = AbstractStrimziTool.parseSchema(schemaJson);

        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.required()).isNull();
    }

    @Test
    void parseSchemaShouldThrowExceptionForInvalidJson() {
        String invalidJson = "{ invalid json }";

        assertThatThrownBy(() -> AbstractStrimziTool.parseSchema(invalidJson))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse JSON schema");
    }

    @Test
    void getStringArgShouldReturnValueWhenPresent() {
        Map<String, Object> args = new HashMap<>();
        args.put("namespace", "my-namespace");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("test", args);

        String result = tool.testGetStringArg(request, "namespace");

        assertThat(result).isEqualTo("my-namespace");
    }

    @Test
    void getStringArgShouldReturnNullWhenNotPresent() {
        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("test", args);

        String result = tool.testGetStringArg(request, "namespace");

        assertThat(result).isNull();
    }

    @Test
    void getStringArgShouldReturnNullForNullRequest() {
        String result = tool.testGetStringArg(null, "namespace");

        assertThat(result).isNull();
    }

    @Test
    void getIntArgShouldReturnValueWhenPresent() {
        Map<String, Object> args = new HashMap<>();
        args.put("partitions", 3);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("test", args);

        int result = tool.testGetIntArg(request, "partitions", 1);

        assertThat(result).isEqualTo(3);
    }

    @Test
    void getIntArgShouldReturnDefaultWhenNotPresent() {
        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("test", args);

        int result = tool.testGetIntArg(request, "partitions", 1);

        assertThat(result).isEqualTo(1);
    }

    @Test
    void getOptionalIntArgShouldReturnValueWhenPresent() {
        Map<String, Object> args = new HashMap<>();
        args.put("replicas", 3);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("test", args);

        Integer result = tool.testGetOptionalIntArg(request, "replicas");

        assertThat(result).isEqualTo(3);
    }

    @Test
    void getOptionalIntArgShouldReturnNullWhenNotPresent() {
        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("test", args);

        Integer result = tool.testGetOptionalIntArg(request, "replicas");

        assertThat(result).isNull();
    }

    @Test
    void getBooleanArgShouldReturnValueWhenPresent() {
        Map<String, Object> args = new HashMap<>();
        args.put("enabled", true);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("test", args);

        boolean result = tool.testGetBooleanArg(request, "enabled", false);

        assertThat(result).isTrue();
    }

    @Test
    void getBooleanArgShouldReturnDefaultWhenNotPresent() {
        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("test", args);

        boolean result = tool.testGetBooleanArg(request, "enabled", false);

        assertThat(result).isFalse();
    }

    @Test
    void getMapArgShouldReturnMapWhenPresent() {
        Map<String, Object> config = new HashMap<>();
        config.put("retention.ms", "86400000");
        config.put("cleanup.policy", "delete");

        Map<String, Object> args = new HashMap<>();
        args.put("config", config);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("test", args);

        Map<String, Object> result = tool.testGetMapArg(request, "config");

        assertThat(result)
                .containsEntry("retention.ms", "86400000")
                .containsEntry("cleanup.policy", "delete");
    }

    @Test
    void getMapArgShouldReturnNullWhenNotPresent() {
        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("test", args);

        Map<String, Object> result = tool.testGetMapArg(request, "config");

        assertThat(result).isNull();
    }

    @Test
    void successShouldCreateSuccessfulResult() {
        CallToolResult result = tool.testSuccess("Operation completed");

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Operation completed");
    }

    @Test
    void errorShouldCreateErrorResult() {
        CallToolResult result = tool.testError("Something went wrong");

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Something went wrong");
    }

    @Test
    void getSpecificationShouldReturnValidSpecification() {
        var spec = tool.getSpecification();

        assertThat(spec.tool().name()).isEqualTo("test_tool");
        assertThat(spec.tool().description()).isEqualTo("Test tool for unit testing");
    }

    /**
     * Test implementation of AbstractStrimziTool for testing protected methods.
     */
    private static class TestStrimziTool extends AbstractStrimziTool {

        protected TestStrimziTool(KubernetesClient kubernetesClient) {
            super(kubernetesClient);
        }

        @Override
        protected String getName() {
            return "test_tool";
        }

        @Override
        protected String getDescription() {
            return "Test tool for unit testing";
        }

        @Override
        protected JsonSchema getInputSchema() {
            return parseSchema("""
                    {
                        "type": "object",
                        "properties": {}
                    }
                    """);
        }

        @Override
        protected CallToolResult execute(McpSchema.CallToolRequest args) {
            return success("Test executed");
        }

        // Expose protected methods for testing
        String testGetStringArg(McpSchema.CallToolRequest args, String key) {
            return getStringArg(args, key);
        }

        int testGetIntArg(McpSchema.CallToolRequest args, String key, int defaultValue) {
            return getIntArg(args, key, defaultValue);
        }

        Integer testGetOptionalIntArg(McpSchema.CallToolRequest args, String key) {
            return getOptionalIntArg(args, key);
        }

        boolean testGetBooleanArg(McpSchema.CallToolRequest args, String key, boolean defaultValue) {
            return getBooleanArg(args, key, defaultValue);
        }

        Map<String, Object> testGetMapArg(McpSchema.CallToolRequest args, String key) {
            return getMapArg(args, key);
        }

        CallToolResult testSuccess(String content) {
            return success(content);
        }

        CallToolResult testError(String message) {
            return error(message);
        }
    }
}
