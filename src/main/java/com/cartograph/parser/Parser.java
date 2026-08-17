/*
 * Parser.java
 * -----------
 * Purpose (plain English): The contract every language reader must satisfy. Cartograph asks
 * a parser "do you handle this file?" and, if so, "what's in it?" — and gets back the same
 * shape of answer no matter which language it was. Adding a new language means adding one
 * more implementation of this interface and nothing else.
 */
package com.cartograph.parser;

import com.cartograph.model.ParsedFile;

import java.nio.file.Path;

public interface Parser {

    /** The language this parser reads, lowercase, e.g. {@code java}. */
    String language();

    /** True if this parser recognises the file, normally by its extension. */
    boolean handles(Path file);

    /**
     * Reads one source file and reports what it declares and what it mentions.
     *
     * <p>Implementations must not throw for malformed input: a file that cannot be read or
     * parsed should come back as {@link ParsedFile#empty}, so one bad file never fails a
     * whole indexing run.
     *
     * @param file         absolute path to the file to read
     * @param relativePath the path to record in the results, relative to the repository root
     */
    ParsedFile parse(Path file, String relativePath);
}
