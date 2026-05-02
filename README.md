# JADX MCP Server

A headless Model Context Protocol (MCP) server for [JADX](https://github.com/skylot/jadx), the popular dex-to-java decompiler. 

This server exposes JADX's powerful reverse engineering and decompilation capabilities directly to AI agents (like Claude Desktop) via standard MCP tool calls. It enables autonomous agents to interactively analyze, search, and decompile Android applications and Java codebases without needing to dump gigabytes of source files to disk.

## Features

- **Headless Decompilation**: Run JADX entirely in the background, controlled by your AI agent.
- **Multi-session Support**: Safely load and analyze multiple APK/DEX files in isolation through unique session IDs.
- **Interactive Toolset**:
  - `load_binary`: Initialize an analysis session for an APK or DEX file.
  - `get_manifest`: Extract and decompile the `AndroidManifest.xml`.
  - `list_classes`: Retrieve a list of all fully-qualified class names, with optional package filtering.
  - `get_class_source`: Fetch the complete decompiled Java source code for a specific class.
  - `get_method_source`: Extract the decompiled Java source code for a specific method, saving context window space.
  - `search_strings`: Global string search across the decompiled AST instructions (supports exact match and case sensitivity).
  - `get_class_xrefs`: Find all cross-references (usages) of a specific class.
  - `get_method_xrefs`: Find all cross-references (usages) of a specific method.
  - `close_session`: Safely close a session and free up memory.

### TODO Features
- [ ] **Project File Support**: Automatically load and save `.jadx` project files to persist renames, bookmarks, and comments between sessions.
- [ ] **XML Resource Search**: Separate string search pass for Android XML resources.
- [ ] **Deobfuscation Tools**: Endpoints to let the agent dynamically rename classes, fields, and methods in the AST.

## Requirements

- Java 17 or higher
- Gradle

## How to Build

Clone the repository and use the Gradle wrapper to build the distribution:

```bash
git clone https://github.com/subwire/jadx-mcp-server.git
cd jadx-mcp-server

# Build the executable distribution
./gradlew installDist
```

This will create a compiled, ready-to-run executable script located at:
`build/install/jadx-mcp-server/bin/jadx-mcp-server`

## Configuration

To use the JADX MCP Server with an MCP-compatible client like Claude Desktop, you need to point the client to the generated executable script.

### Claude Desktop Example

Add the following to your `claude_desktop_config.json` file (typically located at `~/Library/Application Support/Claude/claude_desktop_config.json` on macOS):

```json
{
  "mcpServers": {
    "jadx": {
      "command": "/absolute/path/to/jadx-mcp-server/build/install/jadx-mcp-server/bin/jadx-mcp-server"
    }
  }
}
```

*Note: Make sure to replace `/absolute/path/to/` with the actual absolute path to the repository on your local machine.*

Once configured, restart Claude Desktop. The agent will now have access to the JADX tools and can autonomously reverse engineer Android applications for you.
