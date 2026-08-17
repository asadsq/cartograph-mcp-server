/*
 * SymbolResolver.java
 * -------------------
 * Purpose (plain English): Works out what each mentioned name actually refers to. The parser
 * only sees that a file says "List" or "PingTool"; this class decides whether that means a
 * class in this repo, something from an outside library, or a name it honestly can't place.
 * It follows Java's own lookup order — the file itself, then imports, then the package.
 */
package com.cartograph.resolver;

import com.cartograph.model.ParsedFile;
import com.cartograph.model.Reference;
import com.cartograph.model.Symbol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SymbolResolver {

    /**
     * Types every Java file can use without importing anything. Listing the common ones lets
     * us report them as "from the JDK" rather than as failures, which keeps the coverage
     * number meaningful.
     */
    private static final Set<String> JAVA_LANG = Set.of(
            "Object", "String", "CharSequence", "Integer", "Long", "Short", "Byte", "Double",
            "Float", "Boolean", "Character", "Number", "Math", "System", "Thread", "Runnable",
            "Exception", "RuntimeException", "Error", "Throwable", "IllegalArgumentException",
            "IllegalStateException", "UnsupportedOperationException", "NullPointerException",
            "ClassCastException", "IndexOutOfBoundsException", "InterruptedException",
            "NumberFormatException", "ArithmeticException", "ArrayIndexOutOfBoundsException",
            "StringIndexOutOfBoundsException", "NegativeArraySizeException", "ArrayStoreException",
            "AssertionError", "StackOverflowError", "OutOfMemoryError", "NoSuchFieldException",
            "ClassNotFoundException", "CloneNotSupportedException", "ReflectiveOperationException",
            "NoSuchMethodException", "SecurityException", "StringBuffer", "Appendable", "Readable",
            "Class", "ClassLoader", "Enum", "Record", "Iterable", "Comparable", "Cloneable",
            "AutoCloseable", "StringBuilder", "Override", "Deprecated", "SuppressWarnings",
            "FunctionalInterface", "SafeVarargs", "Process", "ProcessBuilder", "Runtime",
            "StackTraceElement", "ThreadLocal", "Void", "Package", "Module");

    /** Every type declared anywhere in the repo, by fully-qualified name. */
    private final Map<String, Symbol> typesById = new HashMap<>();

    /** The files being resolved, so each reference can be judged in its own file's context. */
    private final List<ParsedFile> files;

    public SymbolResolver(List<ParsedFile> files) {
        this.files = List.copyOf(files);
        for (ParsedFile file : this.files) {
            for (Symbol type : file.types()) {
                typesById.put(type.id(), type);
            }
        }
    }

    /** Every type declared in the repository, keyed by fully-qualified name. */
    public Map<String, Symbol> typesById() {
        return Map.copyOf(typesById);
    }

    /**
     * Goes through every reference the parsers found and decides what it points at.
     * Self-references (a type mentioning itself) are dropped: they are real, but they say
     * nothing about dependencies and would put a loop on every node.
     */
    public ResolutionResult resolve() {
        List<ResolvedReference> edges = new ArrayList<>();
        int internal = 0;
        int external = 0;
        int unresolved = 0;
        int total = 0;

        // A bounded, de-duplicated sample so the coverage note can show real examples.
        Set<String> unresolvedExamples = new LinkedHashSet<>();

        for (ParsedFile file : files) {
            FileContext context = new FileContext(file);

            for (Reference reference : file.references()) {
                total++;
                String targetId = context.resolve(reference);

                if (targetId != null) {
                    internal++;
                    if (!targetId.equals(reference.fromSymbolId())) {
                        edges.add(new ResolvedReference(reference.fromSymbolId(), targetId, reference.kind()));
                    }
                } else if (context.looksExternal(reference)) {
                    external++;
                } else {
                    unresolved++;
                    if (unresolvedExamples.size() < 5) {
                        unresolvedExamples.add(reference.targetName());
                    }
                }
            }
        }

        Coverage coverage = new Coverage(total, internal, external, unresolved, List.copyOf(unresolvedExamples));
        return new ResolutionResult(edges, coverage);
    }

    /**
     * Resolution rules applied from the point of view of one file. Java resolves a bare name
     * by looking at the file, then its imports, then its own package — and so do we.
     */
    private final class FileContext {

        private final ParsedFile file;

        /** Simple name -> fully-qualified id, for types declared in this same file. */
        private final Map<String, String> declaredHere = new HashMap<>();

        /** Simple name -> fully-qualified id, from this file's explicit imports. */
        private final Map<String, String> importedNames = new HashMap<>();

        FileContext(ParsedFile file) {
            this.file = file;
            for (Symbol type : file.types()) {
                declaredHere.putIfAbsent(type.name(), type.id());
            }
            for (String imported : file.imports()) {
                int lastDot = imported.lastIndexOf('.');
                if (lastDot >= 0) {
                    importedNames.putIfAbsent(imported.substring(lastDot + 1), imported);
                }
            }
        }

        /**
         * Returns the id of the repo type this reference points at, or null if it points
         * outside the repo or can't be placed.
         */
        String resolve(Reference reference) {
            String name = reference.targetName();
            return reference.isQualified() ? resolveQualified(name) : resolveSimple(name);
        }

        /** Handles names written with dots, like {@code java.util.List} or {@code Outer.Inner}. */
        private String resolveQualified(String name) {
            if (typesById.containsKey(name)) {
                return name;
            }

            // `Outer.Inner` — the leading part is itself a name needing resolution, and only
            // once that's known can the nested part be appended.
            int firstDot = name.indexOf('.');
            String head = name.substring(0, firstDot);
            String tail = name.substring(firstDot + 1);

            if (!head.isEmpty() && Character.isUpperCase(head.charAt(0))) {
                String resolvedHead = resolveSimple(head);
                if (resolvedHead != null) {
                    String nested = resolvedHead + "." + tail;
                    if (typesById.containsKey(nested)) {
                        return nested;
                    }
                    // The outer type is ours even if we never parsed the nested one.
                    return resolvedHead;
                }
            }
            return null;
        }

        /** Handles bare names like {@code PingTool}, following Java's own lookup order. */
        private String resolveSimple(String name) {
            String sameFile = declaredHere.get(name);
            if (sameFile != null) {
                return sameFile;
            }

            String imported = importedNames.get(name);
            if (imported != null) {
                // An explicit import is definitive: if it isn't ours, it's a library's.
                return typesById.containsKey(imported) ? imported : null;
            }

            String samePackage = Symbol.qualify(file.packageName(), name);
            if (typesById.containsKey(samePackage)) {
                return samePackage;
            }

            // A nested type referred to by its short name from inside the same outer type.
            for (String enclosing : declaredHere.values()) {
                String nested = enclosing + "." + name;
                if (typesById.containsKey(nested)) {
                    return nested;
                }
            }

            for (String wildcard : file.wildcardPackages()) {
                String candidate = wildcard + "." + name;
                if (typesById.containsKey(candidate)) {
                    return candidate;
                }
            }
            return null;
        }

        /**
         * Decides whether a name we couldn't match is genuinely someone else's code rather
         * than a miss on our part. Being strict here keeps the coverage number honest.
         */
        boolean looksExternal(Reference reference) {
            String name = reference.targetName();

            if (reference.isQualified()) {
                // A dotted name starting lowercase is a package path, so it names a real type
                // somewhere — just not one of ours.
                if (Character.isLowerCase(name.charAt(0))) {
                    return true;
                }
                // Otherwise it's a nested type like `McpSchema.Tool`. If we know where the
                // outer type comes from and it isn't ours, the nested one isn't either.
                String head = name.substring(0, name.indexOf('.'));
                return importedNames.containsKey(head) || JAVA_LANG.contains(head);
            }
            if (importedNames.containsKey(name)) {
                return true;
            }
            if (JAVA_LANG.contains(name)) {
                return true;
            }
            // A wildcard import means the name most likely came from that package.
            return !file.wildcardPackages().isEmpty();
        }
    }
}
