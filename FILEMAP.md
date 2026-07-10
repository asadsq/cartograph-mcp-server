<!--
  FILEMAP.md
  ==========
  Purpose (plain English): A single map of the whole project. Every file in the repo is
  listed here with a one-line description of what it does, so anyone can find their way
  around without opening anything. Keep it updated in the same commit as any file you
  add, rename, or delete.
-->

# Cartograph — File Map

A quick-reference index of every file in this repository, grouped by directory.

## Repository root

- **`FILEMAP.md`** — This file. The master index describing every file in the project.
- **`pom.xml`** — The Maven build recipe: Java 21, the MCP SDK dependency, and the packaging step that produces the runnable `target/cartograph.jar`.
- **`.gitignore`** — Lists files Git should never track, such as build output and editor settings.
- **`mvnw`** — Maven Wrapper launcher for macOS and Linux. Lets you run `./mvnw` to build without installing Maven yourself. Vendored from Apache; not edited by hand.
- **`mvnw.cmd`** — The same Maven Wrapper launcher for Windows. Vendored from Apache; not edited by hand.

## `.mvn/wrapper/`

- **`.mvn/wrapper/maven-wrapper.properties`** — Tells `./mvnw` exactly which Maven version to download and verifies its checksum, so every machine and CI runner builds identically.

## `src/main/java/com/cartograph/`

- **`Main.java`** — The program's entry point. Boots the MCP server on stdin/stdout and shuts it down cleanly when the AI agent disconnects.

## `src/main/java/com/cartograph/mcp/`

- **`CartographServer.java`** — Assembles the MCP server: declares the server's name, its capabilities, and registers every tool an agent is allowed to call.

## `src/main/java/com/cartograph/mcp/tools/`

- **`PingTool.java`** — A trivial health-check tool. Takes no input and returns a friendly greeting, confirming the server is running and reachable.
