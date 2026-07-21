/*
 * CodeGraphTest.java
 * ------------------
 * Purpose (plain English): Checks the map answers questions correctly — that walking outwards
 * finds things at the right distance, that looking backwards finds what would be affected by
 * a change, and that a type can be found by its short name.
 */
package com.cartograph.graph;

import com.cartograph.model.ReferenceKind;
import com.cartograph.model.Symbol;
import com.cartograph.model.SymbolKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeGraphTest {

    private CodeGraph graph;

    /** A -> B -> C, plus D -> B, so B has two dependents and C is two steps from A. */
    @BeforeEach
    void buildChain() {
        graph = new CodeGraph();
        for (String name : List.of("A", "B", "C", "D")) {
            graph.addType(new Symbol("com.app." + name, name, SymbolKind.CLASS, "com.app", name + ".java", 1, 5));
        }
        graph.addDependency("com.app.A", "com.app.B", ReferenceKind.IMPORT);
        graph.addDependency("com.app.B", "com.app.C", ReferenceKind.IMPORT);
        graph.addDependency("com.app.D", "com.app.B", ReferenceKind.EXTENDS);
    }

    @Test
    void depthLimitsHowFarTheWalkGoes() {
        assertEquals(List.of("com.app.B"), ids(graph.dependencies("com.app.A", 1)));
        assertEquals(List.of("com.app.B", "com.app.C"), ids(graph.dependencies("com.app.A", 2)));
    }

    @Test
    void recordsHowManyStepsAwayEachResultIs() {
        List<Neighbour> found = graph.dependencies("com.app.A", 3);
        assertEquals(1, found.get(0).distance());
        assertEquals(2, found.get(1).distance());
    }

    @Test
    void dependentsLookBackwardsAlongTheArrows() {
        assertEquals(List.of("com.app.A", "com.app.D"), ids(graph.dependents("com.app.B", 1)));
        // C is used by B, which is used by A and D — so changing C could reach all three.
        assertEquals(3, graph.dependents("com.app.C", 5).size());
    }

    @Test
    void describesOnlyDirectConnections() {
        List<Neighbour> found = graph.dependencies("com.app.A", 2);
        assertEquals(List.of("imports"), found.get(0).via());
        assertTrue(found.get(1).via().isEmpty(), "a type two steps away has no single connection to name");
    }

    @Test
    void repeatedDependenciesStrengthenOneArrowRatherThanAddingMore() {
        int before = graph.dependencyCount();
        graph.addDependency("com.app.A", "com.app.B", ReferenceKind.INSTANTIATION);
        assertEquals(before, graph.dependencyCount());

        List<Neighbour> found = graph.dependencies("com.app.A", 1);
        assertEquals(List.of("creates", "imports"), found.get(0).via());
    }

    @Test
    void findsTypesByShortOrFullName() {
        assertEquals(List.of("com.app.B"), graph.findTypes("B"));
        assertEquals(List.of("com.app.B"), graph.findTypes("com.app.B"));
        assertEquals(List.of("com.app.B"), graph.findTypes("app.B"));
        assertTrue(graph.findTypes("Nonexistent").isEmpty());
    }

    @Test
    void aTypeThatNothingUsesReturnsNothing() {
        assertTrue(graph.dependents("com.app.A", 5).isEmpty());
        assertTrue(graph.dependencies("com.app.C", 5).isEmpty());
    }

    private List<String> ids(List<Neighbour> neighbours) {
        return neighbours.stream().map(Neighbour::id).toList();
    }
}
