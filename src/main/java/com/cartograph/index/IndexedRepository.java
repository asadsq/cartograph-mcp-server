/*
 * IndexedRepository.java
 * ----------------------
 * Purpose (plain English): The finished map of one repository, plus the story of how it was
 * made — how many files were read, how long it took, and how much of the code we could
 * follow. Query tools work against the graph held here.
 */
package com.cartograph.index;

import com.cartograph.graph.CodeGraph;
import com.cartograph.resolver.Coverage;

import java.time.Instant;
import java.util.List;

/**
 * @param path          absolute path of the repository that was indexed
 * @param graph         the dependency map built from it
 * @param coverage      how much of the code was successfully followed
 * @param filesParsed   number of source files read
 * @param filesSkipped  files that matched a language but could not be read or parsed
 * @param languages     the languages actually found
 * @param elapsedMillis how long indexing took
 * @param indexedAt     when it finished
 */
public record IndexedRepository(
        String path,
        CodeGraph graph,
        Coverage coverage,
        int filesParsed,
        int filesSkipped,
        List<String> languages,
        long elapsedMillis,
        Instant indexedAt) {

    public IndexedRepository {
        languages = List.copyOf(languages);
    }
}
