/*
 * ReferenceKind.java
 * ------------------
 * Purpose (plain English): The different ways one piece of code can lean on another — by
 * importing it, extending it, or just mentioning its type. Every dependency edge in the
 * graph records which of these it came from, so we can explain *why* two things are linked.
 */
package com.cartograph.model;

public enum ReferenceKind {

    /** An `import` statement naming the target directly. */
    IMPORT,

    /** The source class is a subclass of the target. */
    EXTENDS,

    /** The source class implements or permits the target interface. */
    IMPLEMENTS,

    /** The target's name is used as a type: a field, parameter, return type or local. */
    TYPE_USE,

    /** The target is instantiated, as in `new Target(...)`. */
    INSTANTIATION,

    /** A static member is read off the target, as in `Target.CONSTANT`. */
    STATIC_ACCESS,

    /** The target is used as an annotation, as in `@Target`. */
    ANNOTATION_USE;

    /** A short phrase a non-engineer would understand, used in tool output. */
    public String label() {
        return switch (this) {
            case IMPORT -> "imports";
            case EXTENDS -> "extends";
            case IMPLEMENTS -> "implements";
            case TYPE_USE -> "uses the type";
            case INSTANTIATION -> "creates";
            case STATIC_ACCESS -> "reads a value from";
            case ANNOTATION_USE -> "is annotated with";
        };
    }
}
