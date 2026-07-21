/*
 * Neighbour.java
 * --------------
 * Purpose (plain English): One answer to "what does this depend on?" — the type we found,
 * how many steps away it is, and how it's connected. Distance 1 means directly connected;
 * distance 2 means connected through one other type, and so on.
 */
package com.cartograph.graph;

import com.cartograph.model.Symbol;

import java.util.List;

/**
 * @param id       the fully-qualified name of the related type
 * @param distance how many dependency steps away it is, starting at 1
 * @param file     the file it is declared in, or null if it isn't declared in this repo
 * @param kind     what sort of thing it is (class, interface, ...), or null if unknown
 * @param via      plain-English descriptions of the direct link, only set at distance 1
 */
public record Neighbour(String id, int distance, String file, String kind, List<String> via) {

    public Neighbour {
        via = via == null ? List.of() : List.copyOf(via);
    }

    /** Builds a neighbour from a known repo type. */
    public static Neighbour of(String id, int distance, Symbol symbol, List<String> via) {
        return new Neighbour(
                id,
                distance,
                symbol == null ? null : symbol.file(),
                symbol == null ? null : symbol.kind().label(),
                via);
    }
}
