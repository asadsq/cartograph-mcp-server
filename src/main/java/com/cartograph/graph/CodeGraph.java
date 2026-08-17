/*
 * CodeGraph.java
 * --------------
 * Purpose (plain English): The map itself. It holds every type in the codebase and the arrows
 * between them, and answers the questions the tools ask: "what does this rely on?" and "what
 * relies on this?". The graph work is done by JGraphT; this class keeps a friendly face on it,
 * including letting you look a type up by its short name.
 */
package com.cartograph.graph;

import com.cartograph.model.ReferenceKind;
import com.cartograph.model.Symbol;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.traverse.BreadthFirstIterator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CodeGraph {

    private final Graph<String, DependencyEdge> graph =
            new DefaultDirectedGraph<>(null, DependencyEdge::new, false);

    private final Map<String, Symbol> types = new HashMap<>();

    /** Short name -> every fully-qualified id sharing it, so users can query by short name. */
    private final Map<String, List<String>> bySimpleName = new HashMap<>();

    /** Adds a type as a node. Safe to call twice for the same type. */
    public void addType(Symbol symbol) {
        if (types.putIfAbsent(symbol.id(), symbol) == null) {
            bySimpleName.computeIfAbsent(symbol.name(), k -> new ArrayList<>()).add(symbol.id());
        }
        graph.addVertex(symbol.id());
    }

    /**
     * Records that {@code fromId} depends on {@code toId}. Repeat calls for the same pair
     * strengthen the existing arrow rather than adding a second one.
     */
    public void addDependency(String fromId, String toId, ReferenceKind kind) {
        if (fromId.equals(toId) || !graph.containsVertex(fromId) || !graph.containsVertex(toId)) {
            return;
        }
        DependencyEdge edge = graph.getEdge(fromId, toId);
        if (edge == null) {
            edge = graph.addEdge(fromId, toId);
        }
        if (edge != null) {
            edge.add(kind);
        }
    }

    public int typeCount() {
        return graph.vertexSet().size();
    }

    public int dependencyCount() {
        return graph.edgeSet().size();
    }

    public boolean contains(String id) {
        return graph.containsVertex(id);
    }

    public Symbol symbol(String id) {
        return types.get(id);
    }

    public Collection<Symbol> types() {
        return List.copyOf(types.values());
    }

    /**
     * Turns whatever the user typed into matching type ids. Accepts a full name
     * ({@code com.app.UserService}), a short name ({@code UserService}), or a partial
     * tail ({@code service.UserService}), and falls back to ignoring case.
     */
    public List<String> findTypes(String query) {
        String trimmed = query.trim();
        if (graph.containsVertex(trimmed)) {
            return List.of(trimmed);
        }

        List<String> exactSimple = bySimpleName.get(trimmed);
        if (exactSimple != null && !exactSimple.isEmpty()) {
            return List.copyOf(exactSimple);
        }

        String suffix = "." + trimmed;
        List<String> tailMatches = graph.vertexSet().stream()
                .filter(id -> id.endsWith(suffix))
                .sorted()
                .toList();
        if (!tailMatches.isEmpty()) {
            return tailMatches;
        }

        String lower = trimmed.toLowerCase();
        return graph.vertexSet().stream()
                .filter(id -> id.toLowerCase().equals(lower)
                        || id.toLowerCase().endsWith("." + lower))
                .sorted()
                .toList();
    }

    /** What {@code id} relies on, out to {@code depth} steps. */
    public List<Neighbour> dependencies(String id, int depth) {
        return walk(graph, id, depth, true);
    }

    /**
     * What relies on {@code id}, out to {@code depth} steps. This is the same walk with every
     * arrow flipped, which is what makes "who would I break?" answerable.
     */
    public List<Neighbour> dependents(String id, int depth) {
        return walk(new EdgeReversedGraph<>(graph), id, depth, false);
    }

    /**
     * Breadth-first walk from a starting type, recording how far away each type it reaches is.
     * Going breadth-first means nodes arrive nearest-first, so we can stop as soon as we pass
     * the requested depth.
     */
    private List<Neighbour> walk(Graph<String, DependencyEdge> view, String start, int depth, boolean outgoing) {
        if (!view.containsVertex(start)) {
            return List.of();
        }

        List<Neighbour> found = new ArrayList<>();
        BreadthFirstIterator<String, DependencyEdge> iterator = new BreadthFirstIterator<>(view, start);

        while (iterator.hasNext()) {
            String node = iterator.next();
            int distance = iterator.getDepth(node);

            if (distance == 0) {
                continue;
            }
            if (distance > depth) {
                break;
            }

            // Only immediate neighbours have a single link to describe; anything further away
            // is reached through a chain, so naming one connection kind would be misleading.
            List<String> via = List.of();
            if (distance == 1) {
                DependencyEdge edge = outgoing ? graph.getEdge(start, node) : graph.getEdge(node, start);
                if (edge != null) {
                    via = edge.labels();
                }
            }

            found.add(Neighbour.of(node, distance, types.get(node), via));
        }

        found.sort((a, b) -> a.distance() != b.distance()
                ? Integer.compare(a.distance(), b.distance())
                : a.id().compareTo(b.id()));
        return found;
    }
}
