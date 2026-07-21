/*
 * Symbol.java
 * -----------
 * Purpose (plain English): One named thing found in the code — a class, an interface, a
 * method — along with where it lives (file and line numbers). These are the "places" on
 * Cartograph's map; the dependency graph is built out of them.
 */
package com.cartograph.model;

import java.util.Objects;

/**
 * @param id         unique, fully-qualified name, e.g. {@code com.cartograph.model.Symbol}
 * @param name       the short name as written in the source, e.g. {@code Symbol}
 * @param kind       what sort of thing this is
 * @param packageName the package it was declared in, or empty for the default package
 * @param file       path to the declaring file, relative to the repository root
 * @param startLine  first line of the declaration (1-based)
 * @param endLine    last line of the declaration (1-based)
 */
public record Symbol(
        String id,
        String name,
        SymbolKind kind,
        String packageName,
        String file,
        int startLine,
        int endLine) {

    public Symbol {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(file, "file");
        packageName = packageName == null ? "" : packageName;
    }

    /**
     * Joins a package and a type name into a fully-qualified id, coping with the
     * default (unnamed) package where there is nothing to prefix.
     */
    public static String qualify(String packageName, String typeName) {
        return packageName == null || packageName.isEmpty() ? typeName : packageName + "." + typeName;
    }
}
