/*
 * Reference.java
 * --------------
 * Purpose (plain English): A raw "this code mentions that name" sighting, straight from the
 * parser and not yet matched to anything. The parser produces these; the resolver turns the
 * ones it can identify into real dependency edges.
 */
package com.cartograph.model;

import java.util.Objects;

/**
 * @param fromSymbolId the id of the symbol doing the referring
 * @param targetName   the name exactly as written in the source, which may be a bare name
 *                     like {@code List} or a fully-qualified one like {@code java.util.List}
 * @param kind         how the name was being used
 * @param file         path to the file containing the mention, relative to the repo root
 * @param line         line number of the mention (1-based)
 */
public record Reference(
        String fromSymbolId,
        String targetName,
        ReferenceKind kind,
        String file,
        int line) {

    public Reference {
        Objects.requireNonNull(fromSymbolId, "fromSymbolId");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(file, "file");
    }

    /** True when the name was written out in full, e.g. {@code java.util.List}. */
    public boolean isQualified() {
        return targetName.indexOf('.') >= 0;
    }

    /** The last segment of the name — {@code List} for {@code java.util.List}. */
    public String simpleName() {
        int lastDot = targetName.lastIndexOf('.');
        return lastDot < 0 ? targetName : targetName.substring(lastDot + 1);
    }
}
