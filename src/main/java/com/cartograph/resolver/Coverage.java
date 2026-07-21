/*
 * Coverage.java
 * -------------
 * Purpose (plain English): An honest scorecard of how much of the code Cartograph could
 * actually follow. It counts the names it matched inside the repo, the ones it recognised as
 * belonging to outside libraries, and the ones it simply couldn't place — so results can say
 * how complete they are instead of pretending to be perfect.
 */
package com.cartograph.resolver;

import java.util.List;

/**
 * @param total      every name the parsers saw mentioned
 * @param internal   names matched to something defined in this repository; these become edges
 * @param external   names confidently identified as living in the JDK or a library
 * @param unresolved names we could not place with confidence
 * @param examples   a few unresolved names, to make the gap concrete rather than abstract
 */
public record Coverage(int total, int internal, int external, int unresolved, List<String> examples) {

    public Coverage {
        examples = List.copyOf(examples);
    }

    /**
     * The share of names we could account for — matched in-repo or identified as external.
     * Rounded to one decimal place; 100.0 when there was nothing to resolve.
     */
    public double percentAccountedFor() {
        if (total == 0) {
            return 100.0;
        }
        return Math.round(((internal + external) * 1000.0) / total) / 10.0;
    }

    /**
     * A one-line summary in plain words, for showing to a person. Deliberately avoids
     * jargon: it says what happened, not what the technique was called.
     */
    public String plainSummary() {
        if (total == 0) {
            return "No code references were found to follow.";
        }
        String base = "Followed %d of %d references (%.1f%%): %d point to code in this repo and %d to outside libraries."
                .formatted(internal + external, total, percentAccountedFor(), internal, external);
        if (unresolved == 0) {
            return base;
        }
        return base + " The other %d couldn't be pinned down — usually short names that could mean more than one thing, or types decided while the program runs."
                .formatted(unresolved);
    }
}
