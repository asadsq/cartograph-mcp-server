/*
 * RepositoryIndexer.java
 * ----------------------
 * Purpose (plain English): Turns a folder of source code into a finished map. It finds the
 * files worth reading, parses them all at once across several threads, works out what refers
 * to what, and assembles the graph. This is the one place that knows the whole recipe.
 */
package com.cartograph.index;

import com.cartograph.graph.CodeGraph;
import com.cartograph.model.ParsedFile;
import com.cartograph.model.Symbol;
import com.cartograph.parser.JavaSourceParser;
import com.cartograph.parser.Parser;
import com.cartograph.resolver.ResolutionResult;
import com.cartograph.resolver.ResolvedReference;
import com.cartograph.resolver.SymbolResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class RepositoryIndexer {

    /**
     * Folders that hold generated output or third-party code. Mapping them would bury the
     * project's own structure under noise, so they are skipped unless asked for explicitly.
     */
    private static final Set<String> ALWAYS_SKIP = Set.of(
            ".git", "target", "build", "out", "bin", "dist", "node_modules",
            ".gradle", ".idea", ".mvn", "venv", ".venv", "__pycache__");

    private final List<Parser> parsers;

    public RepositoryIndexer() {
        this(List.of(new JavaSourceParser()));
    }

    public RepositoryIndexer(List<Parser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    /**
     * Reads every supported source file under {@code root} and builds the dependency map.
     *
     * @param root      the repository to index
     * @param languages restrict to these languages, or empty to use every parser we have
     * @param excludes  extra glob patterns to skip, matched against repo-relative paths
     */
    public IndexedRepository index(Path root, List<String> languages, List<String> excludes) throws IOException {
        long started = System.nanoTime();

        Path repoRoot = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(repoRoot)) {
            throw new IOException("Not a directory: " + repoRoot);
        }

        List<Parser> active = activeParsers(languages);
        if (active.isEmpty()) {
            throw new IOException("No parser available for languages: " + languages);
        }

        List<PathMatcher> excludeMatchers = excludes.stream()
                .map(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob))
                .toList();

        List<Path> files = findSourceFiles(repoRoot, active, excludeMatchers);
        List<ParsedFile> parsed = parseAll(repoRoot, files, active);

        int skipped = (int) parsed.stream().filter(p -> p.symbols().isEmpty() && p.references().isEmpty()).count();

        SymbolResolver resolver = new SymbolResolver(parsed);
        ResolutionResult resolution = resolver.resolve();

        CodeGraph graph = buildGraph(parsed, resolution);

        List<String> found = parsed.stream().map(ParsedFile::language).distinct().sorted().toList();
        long elapsed = (System.nanoTime() - started) / 1_000_000;

        return new IndexedRepository(
                repoRoot.toString(),
                graph,
                resolution.coverage(),
                parsed.size(),
                skipped,
                found,
                elapsed,
                Instant.now());
    }

    private List<Parser> activeParsers(List<String> languages) {
        if (languages == null || languages.isEmpty()) {
            return parsers;
        }
        Set<String> wanted = languages.stream().map(String::toLowerCase).collect(Collectors.toSet());
        return parsers.stream().filter(p -> wanted.contains(p.language())).toList();
    }

    /** Walks the tree once, keeping files some parser recognises and no rule excludes. */
    private List<Path> findSourceFiles(Path root, List<Parser> active, List<PathMatcher> excludes) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(path -> !isInSkippedDirectory(root, path))
                    .filter(path -> active.stream().anyMatch(p -> p.handles(path)))
                    .filter(path -> {
                        Path relative = root.relativize(path);
                        return excludes.stream().noneMatch(matcher -> matcher.matches(relative));
                    })
                    .sorted()
                    .toList();
        } catch (UncheckedIOException e) {
            throw new IOException("Could not read the repository: " + e.getMessage(), e);
        }
    }

    private boolean isInSkippedDirectory(Path root, Path file) {
        Path relative = root.relativize(file);
        for (Path segment : relative) {
            if (ALWAYS_SKIP.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses files across a small pool of threads. The pool is deliberately made of real
     * platform threads: each one keeps its own reusable parser, which would be wasteful if
     * threads were cheap and plentiful.
     */
    private List<ParsedFile> parseAll(Path root, List<Path> files, List<Parser> active) throws IOException {
        if (files.isEmpty()) {
            return List.of();
        }

        int threads = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), files.size()));
        List<ParsedFile> results = new ArrayList<>(files.size());

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<ParsedFile>> futures = new ArrayList<>(files.size());
            for (Path file : files) {
                String relative = root.relativize(file).toString();
                Parser parser = active.stream().filter(p -> p.handles(file)).findFirst().orElseThrow();
                futures.add(pool.submit(() -> parser.parse(file, relative)));
            }
            for (Future<ParsedFile> future : futures) {
                results.add(future.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Indexing was interrupted", e);
        } catch (ExecutionException e) {
            throw new IOException("Failed while parsing: " + e.getCause(), e.getCause());
        }
        return results;
    }

    /** Adds every type as a node first, so no edge can point at a node that doesn't exist yet. */
    private CodeGraph buildGraph(List<ParsedFile> parsed, ResolutionResult resolution) {
        CodeGraph graph = new CodeGraph();
        for (ParsedFile file : parsed) {
            for (Symbol type : file.types()) {
                graph.addType(type);
            }
        }
        for (ResolvedReference reference : resolution.references()) {
            graph.addDependency(reference.fromId(), reference.toId(), reference.kind());
        }
        return graph;
    }

    /** The languages this indexer can currently read. */
    public List<String> supportedLanguages() {
        return parsers.stream().map(Parser::language).distinct().sorted().toList();
    }
}
