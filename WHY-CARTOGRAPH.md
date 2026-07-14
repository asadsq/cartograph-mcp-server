<!--
  WHY-CARTOGRAPH.md
  =================
  Purpose (plain English): Explains what makes Cartograph distinct and how it adds value
  to an AI agent — the "why this exists" companion to the technical docs. Written for a
  reader deciding whether Cartograph is worth adding to their setup.
-->

# Why Cartograph

*A mapmaker for your codebase.*

## Why Cartograph — and what it adds to your agent

- **Parses the repo once and holds the full dependency and call graph in memory.** Instead of working out how the code fits together by reading files and following references for each new question, Cartograph builds the map a single time and keeps it ready — so the agent receives each connection as a compact, ready-made fact, leaving its attention free for the actual work of reasoning and writing code.

- **Answers aggregate, whole-graph questions as single primitives.** Some questions are properties of the *entire system* rather than any one file — and can only be answered by holding the whole map at once. Arriving at them by exploration would mean assembling the full graph first; Cartograph exposes each as one direct call. The four below are exactly this kind of question:

  - **`blast_radius` — the complete transitive closure of dependents, grouped by distance.** When you ask "what breaks if I change this?", it walks every chain of dependencies to the end and returns *all* of them, sorted by how far away they are. Nothing that could be affected is quietly left out, and the agent doesn't have to judge when it has followed far enough — completeness is guaranteed by construction.

  - **`find_cycles` — circular-dependency detection across the whole graph.** Loops where files depend on each other in a ring aren't visible from any single file; they only surface when the entire structure is in view at once, which is precisely what Cartograph can see.

  - **`hotspots` — files ranked by in-degree, the most-depended-on.** It counts how many things rely on each file and ranks them, so the files the rest of the codebase leans on most — and therefore carry the most risk when changed — are visible at a glance.

  - **`path_between` — the shortest dependency path from A to B.** It answers whether two parts of the codebase are connected through their dependencies, and if so, traces the exact chain of steps that links them.

- **Every answer is exhaustive by construction, deterministic, and repeatable.** Because each result is computed over the complete graph rather than sampled from a few paths, the same question always returns the same full answer — stable across a whole session and reliable to build on with confidence.

- **Every result ships with an honest coverage report.** Alongside the data, Cartograph says what it was able to resolve, what it couldn't (for example, calls decided at runtime), and why — so you always know precisely how much to trust the result.

- **Each tool also returns a plain-English `method` line, delivered in one compact call.** Rather than a trail of file reads to digest, the agent gets a small result plus one friendly sentence on what Cartograph checked — so it can turn around a trustworthy, well-grounded answer quickly, with the work behind it visible rather than a black box.

- **Runs entirely on your own machine — your code never leaves it.** Cartograph is launched locally by your MCP client as an ordinary program on your computer, and it reads and maps the repository in memory right there; it doesn't upload your source or depend on any remote service. So you get the full dependency map even on private, proprietary codebases, with nothing sent to the cloud and your code never leaving your laptop.

## The through-line

An agent reasons; Cartograph remembers the structure. The agent brings judgment, semantic understanding, and the actual edits; Cartograph brings the exhaustive, repeatable, structural bookkeeping that's otherwise tedious to reconstruct from scratch — and the agent is more capable with that map in hand than without it.
