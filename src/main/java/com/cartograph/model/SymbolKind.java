/*
 * SymbolKind.java
 * ---------------
 * Purpose (plain English): The list of things Cartograph can find in source code — a class,
 * an interface, a method, and so on. Keeping these as a fixed list means every language
 * parser describes what it found in the same vocabulary.
 */
package com.cartograph.model;

public enum SymbolKind {

    CLASS,
    INTERFACE,
    ENUM,
    RECORD,
    ANNOTATION,
    METHOD,
    CONSTRUCTOR,
    FIELD;

    /** True for kinds that name a type, which are the nodes of the dependency graph. */
    public boolean isType() {
        return this == CLASS || this == INTERFACE || this == ENUM || this == RECORD || this == ANNOTATION;
    }

    /** A lowercase label suitable for showing to a person, e.g. "interface". */
    public String label() {
        return name().toLowerCase();
    }
}
