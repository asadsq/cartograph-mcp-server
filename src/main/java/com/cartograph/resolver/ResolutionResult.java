/*
 * ResolutionResult.java
 * ---------------------
 * Purpose (plain English): What the resolver hands back — the confirmed links between types,
 * plus an honest note about how much of the code it managed to follow.
 */
package com.cartograph.resolver;

import java.util.List;

/**
 * @param references links between types in this repository, possibly with duplicates when
 *                   the same pair is connected in more than one way
 * @param coverage   how much of the code was successfully followed
 */
public record ResolutionResult(List<ResolvedReference> references, Coverage coverage) {

    public ResolutionResult {
        references = List.copyOf(references);
    }
}
