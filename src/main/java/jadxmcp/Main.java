package jadxmcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.List;

public class Main {
    private static final JadxSessionManager sessionManager = new JadxSessionManager();

    public static void main(String[] args) {
        System.err.println("Starting JADX MCP Server...");
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(mapper);
            StdioServerTransportProvider transport = new StdioServerTransportProvider(jsonMapper);
            
            McpSyncServer server = McpServer.sync(transport)
                .serverInfo("jadx-mcp", "1.0.0")
                .tool(
                    Tool.builder()
                        .name("load_binary")
                        .description("Load an APK/DEX file into a new JADX session")
                        .inputSchema(new JsonSchema(
                            "object",
                            Map.of("path", Map.of("type", "string", "description", "Absolute path to the APK/DEX file")),
                            List.of("path"),
                            null, null, null
                        ))
                        .build(),
                    (exchange, arguments) -> {
                        String path = (String) arguments.get("path");
                        try {
                            String sessionId = sessionManager.createSession(path);
                            return new CallToolResult(List.of(new TextContent("Session created: " + sessionId)), false);
                        } catch (Exception e) {
                            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
                        }
                    }
                )
                .tool(
                    Tool.builder()
                        .name("list_classes")
                        .description("List all decompiled classes in the session")
                        .inputSchema(new JsonSchema(
                            "object",
                            Map.of(
                                "session_id", Map.of("type", "string", "description", "The session ID"),
                                "package_prefix", Map.of("type", "string", "description", "Optional package prefix to filter classes")
                            ),
                            List.of("session_id"),
                            null, null, null
                        ))
                        .build(),
                    (exchange, arguments) -> {
                        String sessionId = (String) arguments.get("session_id");
                        String packagePrefix = (String) arguments.get("package_prefix");
                        try {
                            List<String> classes = sessionManager.listClasses(sessionId, packagePrefix);
                            String res = String.join("\n", classes);
                            return new CallToolResult(List.of(new TextContent(res.isEmpty() ? "No classes found." : res)), false);
                        } catch (Exception e) {
                            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
                        }
                    }
                )
                .tool(
                    Tool.builder()
                        .name("get_class_source")
                        .description("Get the decompiled Java source code for a class")
                        .inputSchema(new JsonSchema(
                            "object",
                            Map.of(
                                "session_id", Map.of("type", "string", "description", "The session ID"),
                                "class_name", Map.of("type", "string", "description", "Fully qualified class name")
                            ),
                            List.of("session_id", "class_name"),
                            null, null, null
                        ))
                        .build(),
                    (exchange, arguments) -> {
                        String sessionId = (String) arguments.get("session_id");
                        String className = (String) arguments.get("class_name");
                        try {
                            String code = sessionManager.getClassSource(sessionId, className);
                            return new CallToolResult(List.of(new TextContent(code)), false);
                        } catch (Exception e) {
                            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
                        }
                    }
                )
                .tool(
                    Tool.builder()
                        .name("get_manifest")
                        .description("Get the AndroidManifest.xml for the session")
                        .inputSchema(new JsonSchema(
                            "object",
                            Map.of(
                                "session_id", Map.of("type", "string", "description", "The session ID")
                            ),
                            List.of("session_id"),
                            null, null, null
                        ))
                        .build(),
                    (exchange, arguments) -> {
                        String sessionId = (String) arguments.get("session_id");
                        try {
                            String manifest = sessionManager.getManifest(sessionId);
                            return new CallToolResult(List.of(new TextContent(manifest)), false);
                        } catch (Exception e) {
                            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
                        }
                    }
                )
                .tool(
                    Tool.builder()
                        .name("get_method_source")
                        .description("Get the decompiled Java source code for a specific method")
                        .inputSchema(new JsonSchema(
                            "object",
                            Map.of(
                                "session_id", Map.of("type", "string", "description", "The session ID"),
                                "class_name", Map.of("type", "string", "description", "Fully qualified class name"),
                                "method_name", Map.of("type", "string", "description", "Method name")
                            ),
                            List.of("session_id", "class_name", "method_name"),
                            null, null, null
                        ))
                        .build(),
                    (exchange, arguments) -> {
                        String sessionId = (String) arguments.get("session_id");
                        String className = (String) arguments.get("class_name");
                        String methodName = (String) arguments.get("method_name");
                        try {
                            String code = sessionManager.getMethodSource(sessionId, className, methodName);
                            return new CallToolResult(List.of(new TextContent(code)), false);
                        } catch (Exception e) {
                            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
                        }
                    }
                )
                .tool(
                    Tool.builder()
                        .name("close_session")
                        .description("Close a JADX session and free memory")
                        .inputSchema(new JsonSchema(
                            "object",
                            Map.of(
                                "session_id", Map.of("type", "string", "description", "The session ID")
                            ),
                            List.of("session_id"),
                            null, null, null
                        ))
                        .build(),
                    (exchange, arguments) -> {
                        String sessionId = (String) arguments.get("session_id");
                        try {
                            sessionManager.closeSession(sessionId);
                            return new CallToolResult(List.of(new TextContent("Session closed: " + sessionId)), false);
                        } catch (Exception e) {
                            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
                        }
                    }
                )
                .build();
            
            System.err.println("Server initialized. Awaiting requests.");
            
            // Keep alive
            Thread.currentThread().join();
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
