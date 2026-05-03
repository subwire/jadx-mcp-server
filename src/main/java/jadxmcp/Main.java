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
                        .name("list_sessions")
                        .description("List all active sessions and their loaded input files")
                        .inputSchema(new JsonSchema("object", Map.of(), List.of(), null, null, null))
                        .build(),
                    (exchange, arguments) -> {
                        try {
                            Map<String, String> sessions = sessionManager.listSessions();
                            StringBuilder sb = new StringBuilder();
                            for (Map.Entry<String, String> entry : sessions.entrySet()) {
                                sb.append(entry.getKey()).append(" -> ").append(entry.getValue()).append("\n");
                            }
                            String res = sb.toString();
                            return new CallToolResult(List.of(new TextContent(res.isEmpty() ? "No active sessions." : res.trim())), false);
                        } catch (Exception e) {
                            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
                        }
                    }
                )
                .tool(
                    Tool.builder()
                        .name("list_methods")
                        .description("List all methods (with signatures) in a class")
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
                            List<String> methods = sessionManager.listMethods(sessionId, className);
                            String res = String.join("\n", methods);
                            return new CallToolResult(List.of(new TextContent(res.isEmpty() ? "No methods found." : res)), false);
                        } catch (Exception e) {
                            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
                        }
                    }
                )
                .tool(
                    Tool.builder()
                        .name("list_fields")
                        .description("List all fields (with types) in a class")
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
                            List<String> fields = sessionManager.listFields(sessionId, className);
                            String res = String.join("\n", fields);
                            return new CallToolResult(List.of(new TextContent(res.isEmpty() ? "No fields found." : res)), false);
                        } catch (Exception e) {
                            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
                        }
                    }
                )
                .tool(
                    Tool.builder()
                        .name("list_resources")
                        .description("List all resource paths available in the loaded session")
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
                            List<String> resources = sessionManager.listResources(sessionId);
                            String res = String.join("\n", resources);
                            return new CallToolResult(List.of(new TextContent(res.isEmpty() ? "No resources found." : res)), false);
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
                        .name("search_strings")
                        .description("Search for a string across all decompiled code (AST instructions)")
                        .inputSchema(new JsonSchema(
                            "object",
                            Map.of(
                                "session_id", Map.of("type", "string", "description", "The session ID"),
                                "search_string", Map.of("type", "string", "description", "The string to search for"),
                                "exact_match", Map.of("type", "boolean", "description", "Whether to match the string exactly (default: false)"),
                                "case_sensitive", Map.of("type", "boolean", "description", "Whether the search should be case sensitive (default: true)")
                            ),
                            List.of("session_id", "search_string"),
                            null, null, null
                        ))
                        .build(),
                    (exchange, arguments) -> {
                        String sessionId = (String) arguments.get("session_id");
                        String searchString = (String) arguments.get("search_string");
                        boolean exactMatch = arguments.containsKey("exact_match") ? (Boolean) arguments.get("exact_match") : false;
                        boolean caseSensitive = arguments.containsKey("case_sensitive") ? (Boolean) arguments.get("case_sensitive") : true;
                        try {
                            List<String> results = sessionManager.searchStrings(sessionId, searchString, exactMatch, caseSensitive);
                            String res = String.join("\n", results);
                            return new CallToolResult(List.of(new TextContent(res.isEmpty() ? "No matches found." : res)), false);
                        } catch (Exception e) {
                            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
                        }
                    }
                )
                .tool(
                    Tool.builder()
                        .name("get_class_xrefs")
                        .description("Find all cross-references (usages) of a specific class")
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
                            List<String> usages = sessionManager.getClassXrefs(sessionId, className);
                            String res = String.join("\n", usages);
                            return new CallToolResult(List.of(new TextContent(res.isEmpty() ? "No cross-references found." : res)), false);
                        } catch (Exception e) {
                            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
                        }
                    }
                )
                .tool(
                    Tool.builder()
                        .name("get_method_xrefs")
                        .description("Find all cross-references (usages) of a specific method")
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
                            List<String> usages = sessionManager.getMethodXrefs(sessionId, className, methodName);
                            String res = String.join("\n", usages);
                            return new CallToolResult(List.of(new TextContent(res.isEmpty() ? "No cross-references found." : res)), false);
                        } catch (Exception e) {
                            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
                        }
                    }
                )
                .tool(
                    Tool.builder()
                        .name("rename_class")
                        .description("Rename a class (deobfuscation). Source will reflect the new name.")
                        .inputSchema(new JsonSchema(
                            "object",
                            Map.of(
                                "session_id", Map.of("type", "string", "description", "The session ID"),
                                "class_name", Map.of("type", "string", "description", "Current fully qualified class name or original name"),
                                "new_name", Map.of("type", "string", "description", "New class name")
                            ),
                            List.of("session_id", "class_name", "new_name"),
                            null, null, null
                        ))
                        .build(),
                    (exchange, arguments) -> {
                        String sessionId = (String) arguments.get("session_id");
                        String className = (String) arguments.get("class_name");
                        String newName = (String) arguments.get("new_name");
                        try {
                            sessionManager.renameClass(sessionId, className, newName);
                            return new CallToolResult(List.of(new TextContent("Class renamed to: " + newName)), false);
                        } catch (Exception e) {
                            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
                        }
                    }
                )
                .tool(
                    Tool.builder()
                        .name("rename_method")
                        .description("Rename a method (deobfuscation). Source will reflect the new name.")
                        .inputSchema(new JsonSchema(
                            "object",
                            Map.of(
                                "session_id", Map.of("type", "string", "description", "The session ID"),
                                "class_name", Map.of("type", "string", "description", "Fully qualified class name"),
                                "method_name", Map.of("type", "string", "description", "Current method name or original name"),
                                "new_name", Map.of("type", "string", "description", "New method name")
                            ),
                            List.of("session_id", "class_name", "method_name", "new_name"),
                            null, null, null
                        ))
                        .build(),
                    (exchange, arguments) -> {
                        String sessionId = (String) arguments.get("session_id");
                        String className = (String) arguments.get("class_name");
                        String methodName = (String) arguments.get("method_name");
                        String newName = (String) arguments.get("new_name");
                        try {
                            sessionManager.renameMethod(sessionId, className, methodName, newName);
                            return new CallToolResult(List.of(new TextContent("Method renamed to: " + newName)), false);
                        } catch (Exception e) {
                            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
                        }
                    }
                )
                .tool(
                    Tool.builder()
                        .name("rename_field")
                        .description("Rename a field (deobfuscation). Source will reflect the new name.")
                        .inputSchema(new JsonSchema(
                            "object",
                            Map.of(
                                "session_id", Map.of("type", "string", "description", "The session ID"),
                                "class_name", Map.of("type", "string", "description", "Fully qualified class name"),
                                "field_name", Map.of("type", "string", "description", "Current field name or original name"),
                                "new_name", Map.of("type", "string", "description", "New field name")
                            ),
                            List.of("session_id", "class_name", "field_name", "new_name"),
                            null, null, null
                        ))
                        .build(),
                    (exchange, arguments) -> {
                        String sessionId = (String) arguments.get("session_id");
                        String className = (String) arguments.get("class_name");
                        String fieldName = (String) arguments.get("field_name");
                        String newName = (String) arguments.get("new_name");
                        try {
                            sessionManager.renameField(sessionId, className, fieldName, newName);
                            return new CallToolResult(List.of(new TextContent("Field renamed to: " + newName)), false);
                        } catch (Exception e) {
                            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
                        }
                    }
                )
                .tool(
                    Tool.builder()
                        .name("search_xml")
                        .description("Search for a string across all decompiled resources (e.g. AndroidManifest, layouts, strings).")
                        .inputSchema(new JsonSchema(
                            "object",
                            Map.of(
                                "session_id", Map.of("type", "string", "description", "The session ID"),
                                "search_string", Map.of("type", "string", "description", "The string to search for"),
                                "exact_match", Map.of("type", "boolean", "description", "Whether to match exactly (default: false)"),
                                "case_sensitive", Map.of("type", "boolean", "description", "Case sensitive search (default: false)"),
                                "xml_only", Map.of("type", "boolean", "description", "Restrict search to .xml files only (default: false)")
                            ),
                            List.of("session_id", "search_string"),
                            null, null, null
                        ))
                        .build(),
                    (exchange, arguments) -> {
                        String sessionId = (String) arguments.get("session_id");
                        String searchString = (String) arguments.get("search_string");
                        boolean exactMatch = arguments.containsKey("exact_match") ? (Boolean) arguments.get("exact_match") : false;
                        boolean caseSensitive = arguments.containsKey("case_sensitive") ? (Boolean) arguments.get("case_sensitive") : false;
                        boolean xmlOnly = arguments.containsKey("xml_only") ? (Boolean) arguments.get("xml_only") : false;
                        try {
                            List<String> results = sessionManager.searchXml(sessionId, searchString, exactMatch, caseSensitive, xmlOnly);
                            String res = String.join("\n", results);
                            return new CallToolResult(List.of(new TextContent(res.isEmpty() ? "No matches found." : res)), false);
                        } catch (Exception e) {
                            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
                        }
                    }
                )
                .tool(
                    Tool.builder()
                        .name("get_resource")
                        .description("Get the full decompiled text content of a specific resource file (e.g. res/layout/main.xml)")
                        .inputSchema(new JsonSchema(
                            "object",
                            Map.of(
                                "session_id", Map.of("type", "string", "description", "The session ID"),
                                "resource_path", Map.of("type", "string", "description", "The original resource path")
                            ),
                            List.of("session_id", "resource_path"),
                            null, null, null
                        ))
                        .build(),
                    (exchange, arguments) -> {
                        String sessionId = (String) arguments.get("session_id");
                        String resourcePath = (String) arguments.get("resource_path");
                        try {
                            String code = sessionManager.getResource(sessionId, resourcePath);
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
