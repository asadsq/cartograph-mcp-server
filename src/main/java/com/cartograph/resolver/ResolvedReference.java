/*
 * ResolvedReference.java
 * ----------------------
 * Purpose (plain English): A confirmed link between two types in this repository — "this one
 * depends on that one, and here's how". These are exactly the edges that get drawn on
 * Cartograph's map.
 */
package com.cartograph.resolver;

import com.cartograph.model.ReferenceKind;

/**
 * @param fromId the depending type's fully-qualified name
 * @param toId   the depended-on type's fully-qualified name
 * @param kind   the sort of dependency this is
 */
public record ResolvedReference(String fromId, String toId, ReferenceKind kind) {
}
