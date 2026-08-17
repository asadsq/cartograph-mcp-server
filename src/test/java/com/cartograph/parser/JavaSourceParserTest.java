/*
 * JavaSourceParserTest.java
 * -------------------------
 * Purpose (plain English): Checks that reading a Java file finds the right things — the
 * package, the imports, the classes and methods declared, and the other types mentioned —
 * and that it ignores look-alikes such as `var` and constant names.
 */
package com.cartograph.parser;

import com.cartograph.model.ParsedFile;
import com.cartograph.model.Reference;
import com.cartograph.model.ReferenceKind;
import com.cartograph.model.Symbol;
import com.cartograph.model.SymbolKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaSourceParserTest {

    @TempDir
    Path workspace;

    private final JavaSourceParser parser = new JavaSourceParser();

    private ParsedFile parse(String fileName, String source) throws IOException {
        Path file = workspace.resolve(fileName);
        Files.writeString(file, source);
        return parser.parse(file, fileName);
    }

    private Set<String> targetNames(ParsedFile parsed) {
        return parsed.references().stream().map(Reference::targetName).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void readsPackageAndImports() throws IOException {
        ParsedFile parsed = parse("Service.java", """
                package com.app.core;

                import com.app.data.Repository;
                import java.util.*;
                import static com.app.util.Helpers.trim;

                class Service {
                }
                """);

        assertEquals("com.app.core", parsed.packageName());
        assertTrue(parsed.imports().contains("com.app.data.Repository"));
        // A static import names a member, so the type is what we keep.
        assertTrue(parsed.imports().contains("com.app.util.Helpers"));
        assertEquals(List.of("java.util"), parsed.wildcardPackages());
    }

    @Test
    void findsDeclarationsWithFullyQualifiedIds() throws IOException {
        ParsedFile parsed = parse("Service.java", """
                package com.app;

                class Service {
                    private String name;
                    Service() { }
                    void run() { }
                    interface Listener { }
                }
                """);

        assertEquals(List.of("com.app.Service", "com.app.Service.Listener"),
                parsed.types().stream().map(Symbol::id).toList());

        Set<String> ids = parsed.symbols().stream().map(Symbol::id).collect(java.util.stream.Collectors.toSet());
        assertTrue(ids.contains("com.app.Service#run"), "expected the method to be recorded");
        assertTrue(ids.contains("com.app.Service#name"), "expected the field to be recorded");

        Symbol listener = parsed.types().stream()
                .filter(s -> s.id().equals("com.app.Service.Listener")).findFirst().orElseThrow();
        assertEquals(SymbolKind.INTERFACE, listener.kind());
    }

    @Test
    void classifiesHowEachTypeIsUsed() throws IOException {
        ParsedFile parsed = parse("Service.java", """
                package com.app;

                class Service extends BaseService implements Runnable {
                    void run() {
                        Widget w = new Widget();
                        Helpers.trim("x");
                    }
                }
                """);

        assertEquals(ReferenceKind.EXTENDS, kindOf(parsed, "BaseService"));
        assertEquals(ReferenceKind.IMPLEMENTS, kindOf(parsed, "Runnable"));
        assertEquals(ReferenceKind.STATIC_ACCESS, kindOf(parsed, "Helpers"));
        assertTrue(parsed.references().stream()
                        .anyMatch(r -> r.targetName().equals("Widget") && r.kind() == ReferenceKind.INSTANTIATION),
                "expected `new Widget()` to be recorded as creating a Widget");
    }

    @Test
    void ignoresNamesThatOnlyLookLikeTypes() throws IOException {
        ParsedFile parsed = parse("Service.java", """
                package com.app;

                import java.util.List;

                class Service<T> {
                    private static final List<String> DEFAULTS = List.of();
                    T pick(T candidate) {
                        var chosen = candidate;
                        return chosen;
                    }
                }
                """);

        Set<String> names = targetNames(parsed);
        assertFalse(names.contains("var"), "`var` is not a type");
        assertFalse(names.contains("T"), "`T` is a generic placeholder, not a type");
        assertFalse(names.contains("DEFAULTS"), "`DEFAULTS` is a constant, not a type");
        assertTrue(names.contains("List"), "a real type should still be recorded");
    }

    @Test
    void survivesUnparseableInput() throws IOException {
        ParsedFile parsed = parse("Broken.java", "class Broken { this is not valid java (((");
        // The point is that it returns rather than throwing; one bad file must not stop a run.
        assertEquals("Broken.java", parsed.path());
    }

    private ReferenceKind kindOf(ParsedFile parsed, String targetName) {
        return parsed.references().stream()
                .filter(r -> r.targetName().equals(targetName))
                .map(Reference::kind)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no reference to " + targetName));
    }
}
