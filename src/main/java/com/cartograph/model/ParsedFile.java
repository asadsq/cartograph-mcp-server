/*
 * ParsedFile.java
 * ---------------
 * Purpose (plain English): Everything Cartograph learned from reading one source file — the
 * package it belongs to, what it imports, what it declares, and every other name it mentions.
 * This is the handoff between the parser and the resolver.
 */
package com.cartograph.model;

import java.util.List;

/**
 * @param path             file path relative to the repository root
 * @param language         the language this file was parsed as, e.g. {@code java}
 * @param packageName      declared package, or empty for the default package
 * @param imports          fully-qualified names from explicit single-type imports
 * @param wildcardPackages packages brought in wholesale by {@code import foo.*}
 * @param symbols          the declarations found in this file
 * @param references       every name this file mentions, unresolved
 */
public record ParsedFile(
        String path,
        String language,
        String packageName,
        List<String> imports,
        List<String> wildcardPackages,
        List<Symbol> symbols,
        List<Reference> references) {

    public ParsedFile {
        packageName = packageName == null ? "" : packageName;
        imports = List.copyOf(imports);
        wildcardPackages = List.copyOf(wildcardPackages);
        symbols = List.copyOf(symbols);
        references = List.copyOf(references);
    }

    /** An empty result, used when a file could not be read or parsed. */
    public static ParsedFile empty(String path, String language) {
        return new ParsedFile(path, language, "", List.of(), List.of(), List.of(), List.of());
    }

    /** Just the type declarations — the ones that become nodes in the graph. */
    public List<Symbol> types() {
        return symbols.stream().filter(s -> s.kind().isType()).toList();
    }
}
