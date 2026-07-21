/*
 * GraphStore.java
 * ---------------
 * Purpose (plain English): Remembers the maps we've already built, so asking ten questions
 * about a repository only parses it once. It also remembers which repository was indexed most
 * recently, because that's the one the query tools answer about.
 */
package com.cartograph.index;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GraphStore {

    private final Map<String, IndexedRepository> byPath = new ConcurrentHashMap<>();

    /**
     * Written and read from different tool calls, which the MCP server may handle on
     * different threads, so it is guarded rather than a plain field.
     */
    private volatile IndexedRepository mostRecent;

    /** Files a freshly indexed repository, replacing any earlier map of the same path. */
    public void put(IndexedRepository repository) {
        byPath.put(repository.path(), repository);
        mostRecent = repository;
    }

    /** The repository indexed most recently, or null if nothing has been indexed yet. */
    public IndexedRepository mostRecent() {
        return mostRecent;
    }

    /** A previously indexed repository by its absolute path, or null if it isn't cached. */
    public IndexedRepository get(String path) {
        return byPath.get(path);
    }

    /** How many distinct repositories are currently held. */
    public int size() {
        return byPath.size();
    }
}
