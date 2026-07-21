/*
 * DependencyEdge.java
 * -------------------
 * Purpose (plain English): One arrow on the map, meaning "this type depends on that one".
 * It remembers every way the two are connected — imported, extended, instantiated — so we
 * can explain the link rather than just assert it.
 */
package com.cartograph.graph;

import com.cartograph.model.ReferenceKind;
import org.jgrapht.graph.DefaultEdge;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class DependencyEdge extends DefaultEdge {

    private final Set<ReferenceKind> kinds = EnumSet.noneOf(ReferenceKind.class);
    private int occurrences;

    /** Records another sighting of this same dependency. */
    void add(ReferenceKind kind) {
        kinds.add(kind);
        occurrences++;
    }

    /** The distinct ways the two types are connected. */
    public Set<ReferenceKind> kinds() {
        return Collections.unmodifiableSet(kinds);
    }

    /** How many times the dependency was seen in the source. */
    public int occurrences() {
        return occurrences;
    }

    /** The connection kinds as plain-English phrases, e.g. {@code ["imports", "creates"]}. */
    public List<String> labels() {
        return kinds.stream().map(ReferenceKind::label).sorted().toList();
    }

    public String source() {
        return (String) getSource();
    }

    public String target() {
        return (String) getTarget();
    }
}
