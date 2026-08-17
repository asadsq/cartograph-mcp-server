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
- **`WHY-CARTOGRAPH.md`** — Explains what makes Cartograph distinct and how it adds value to an AI agent; the "why this exists" narrative.
- **`pom.xml`** — The Maven build recipe: Java 21, the MCP SDK, tree-sitter and JGraphT dependencies, and the packaging step that produces the runnable `target/cartograph.jar`.
- **`.gitignore`** — Lists files Git should never track, such as build output and editor settings.
- **`mvnw`** — Maven Wrapper launcher for macOS and Linux. Lets you run `./mvnw` to build without installing Maven yourself. Vendored from Apache; not edited by hand.
- **`mvnw.cmd`** — The same Maven Wrapper launcher for Windows. Vendored from Apache; not edited by hand.

## `.mvn/wrapper/`

- **`.mvn/wrapper/maven-wrapper.properties`** — Tells `./mvnw` exactly which Maven version to download and verifies its checksum, so every machine and CI runner builds identically.

## `src/main/java/com/cartograph/`

- **`Main.java`** — The program's entry point. Boots the MCP server on stdin/stdout and shuts it down cleanly when the AI agent disconnects.

## `src/main/java/com/cartograph/mcp/`

- **`CartographServer.java`** — Assembles the MCP server: declares the server's name, its capabilities, and registers every tool an agent is allowed to call.

## `src/main/java/com/cartograph/model/`

- **`Symbol.java`** — One named thing found in the code (a class, a method) and where it lives. These are the places on Cartograph's map.
- **`SymbolKind.java`** — The fixed list of things a parser can find: class, interface, enum, record, annotation, method, constructor, field.
- **`Reference.java`** — A raw "this code mentions that name" sighting from the parser, before anyone has worked out what it points at.
- **`ReferenceKind.java`** — The ways one piece of code can lean on another: importing, extending, implementing, creating, and so on.
- **`ParsedFile.java`** — Everything learned from reading one source file: its package, imports, declarations and mentions. The handoff from parser to resolver.

## `src/main/java/com/cartograph/parser/`

- **`Parser.java`** — The contract every language reader must satisfy, so adding a language means adding one class and changing nothing else.
- **`JavaSourceParser.java`** — Reads Java files using tree-sitter, pulling out the package, imports, declarations and every type mentioned. Works on code that doesn't compile.

## `src/main/java/com/cartograph/resolver/`

- **`SymbolResolver.java`** — Decides what each mentioned name actually refers to: a class in this repo, an outside library, or something it honestly can't place.
- **`ResolvedReference.java`** — A confirmed link between two types in this repository — exactly what becomes an arrow on the map.
- **`ResolutionResult.java`** — What the resolver hands back: the confirmed links plus the coverage scorecard.
- **`Coverage.java`** — An honest scorecard of how much of the code could actually be followed, including a plain-English summary for the user.

## `src/main/java/com/cartograph/graph/`

- **`CodeGraph.java`** — The map itself. Holds every type and the arrows between them, and answers "what does this rely on?" and "what relies on this?".
- **`DependencyEdge.java`** — One arrow on the map, remembering every way two types are connected so the link can be explained rather than just asserted.
- **`Neighbour.java`** — One answer to a graph question: the type found, how many steps away it is, and how it's connected.

## `src/main/java/com/cartograph/index/`

- **`RepositoryIndexer.java`** — Turns a folder of source code into a finished map: finds the files, parses them across several threads, resolves names, builds the graph.
- **`IndexedRepository.java`** — The finished map of one repository plus the story of how it was made: files read, time taken, coverage.
- **`GraphStore.java`** — Remembers maps already built so repeated questions don't re-parse, and tracks which repository was indexed most recently.

## `src/main/java/com/cartograph/mcp/tools/`

- **`PingTool.java`** — A trivial health-check tool. Takes no input and returns a friendly greeting, confirming the server is running and reachable.
- **`IndexRepoTool.java`** — The `index_repo` tool: reads a repository and builds its map. Must be run before any question can be asked.
- **`GetDependenciesTool.java`** — The `get_dependencies` tool: answers "what does this class rely on?", optionally following the chain outwards.
- **`GetDependentsTool.java`** — The `get_dependents` tool: answers "what would I break if I changed this?" by looking backwards along the arrows.
- **`GraphLookup.java`** — Shared groundwork for the question tools: check a repo was indexed, work out which type the user meant, and report ambiguity helpfully.
- **`ToolReply.java`** — Puts every tool's answer into the same shape: the data as JSON plus a plain-English line saying what the tool did.

## `src/test/java/com/cartograph/parser/`

- **`JavaSourceParserTest.java`** — Checks that reading a Java file finds the right declarations and mentions, and ignores look-alikes such as `var` and constant names.

## `src/test/java/com/cartograph/resolver/`

- **`SymbolResolverTest.java`** — Checks names get matched to the right thing (same package, imported, or external) and that the coverage figures are truthful.

## `src/test/java/com/cartograph/graph/`

- **`CodeGraphTest.java`** — Checks the map answers correctly: right distances walking outwards, right results looking backwards, and lookup by short name.

## `src/test/java/com/cartograph/index/`

- **`RepositoryIndexerTest.java`** — Checks the whole job end to end on a small sample repo, including skipping build output and honouring exclusions.
