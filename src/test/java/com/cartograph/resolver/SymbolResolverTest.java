/*
 * SymbolResolverTest.java
 * -----------------------
 * Purpose (plain English): Checks that a mentioned name gets matched to the right thing —
 * a class in the same package, one that was imported, or an outside library — and that the
 * coverage figures reported afterwards are truthful.
 */
package com.cartograph.resolver;

import com.cartograph.model.ParsedFile;
import com.cartograph.model.Reference;
import com.cartograph.model.ReferenceKind;
import com.cartograph.model.Symbol;
import com.cartograph.model.SymbolKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolResolverTest {

    private static Symbol type(String id, String name, String pkg, String file) {
        return new Symbol(id, name, SymbolKind.CLASS, pkg, file, 1, 10);
    }

    private static ParsedFile file(String path, String pkg, List<String> imports, List<Symbol> symbols,
                                   List<Reference> references) {
        return new ParsedFile(path, "java", pkg, imports, List.of(), symbols, references);
    }

    @Test
    void matchesTypesInTheSamePackageWithoutAnImport() {
        ParsedFile service = file("Service.java", "com.app",
                List.of(),
                List.of(type("com.app.Service", "Service", "com.app", "Service.java")),
                List.of(new Reference("com.app.Service", "Helper", ReferenceKind.TYPE_USE, "Service.java", 5)));

        ParsedFile helper = file("Helper.java", "com.app",
                List.of(),
                List.of(type("com.app.Helper", "Helper", "com.app", "Helper.java")),
                List.of());

        ResolutionResult result = new SymbolResolver(List.of(service, helper)).resolve();

        assertEquals(1, result.references().size());
        assertEquals("com.app.Service", result.references().get(0).fromId());
        assertEquals("com.app.Helper", result.references().get(0).toId());
        assertEquals(1, result.coverage().internal());
        assertEquals(0, result.coverage().unresolved());
    }

    @Test
    void followsAnExplicitImportAcrossPackages() {
        ParsedFile service = file("Service.java", "com.app.core",
                List.of("com.app.data.Repository"),
                List.of(type("com.app.core.Service", "Service", "com.app.core", "Service.java")),
                List.of(new Reference("com.app.core.Service", "Repository", ReferenceKind.TYPE_USE, "Service.java", 7)));

        ParsedFile repository = file("Repository.java", "com.app.data",
                List.of(),
                List.of(type("com.app.data.Repository", "Repository", "com.app.data", "Repository.java")),
                List.of());

        ResolutionResult result = new SymbolResolver(List.of(service, repository)).resolve();

        Set<String> targets = result.references().stream()
                .map(ResolvedReference::toId).collect(Collectors.toSet());
        assertTrue(targets.contains("com.app.data.Repository"));
    }

    @Test
    void countsLibraryTypesAsExternalRatherThanFailures() {
        ParsedFile service = file("Service.java", "com.app",
                List.of("java.util.List"),
                List.of(type("com.app.Service", "Service", "com.app", "Service.java")),
                List.of(
                        new Reference("com.app.Service", "List", ReferenceKind.TYPE_USE, "Service.java", 4),
                        new Reference("com.app.Service", "String", ReferenceKind.TYPE_USE, "Service.java", 5)));

        ResolutionResult result = new SymbolResolver(List.of(service)).resolve();

        assertEquals(0, result.coverage().internal());
        assertEquals(2, result.coverage().external(), "an imported type and a java.lang type are both external");
        assertEquals(0, result.coverage().unresolved());
        assertTrue(result.references().isEmpty(), "library types are not nodes on the map");
    }

    @Test
    void reportsNamesItCannotPlace() {
        ParsedFile service = file("Service.java", "com.app",
                List.of(),
                List.of(type("com.app.Service", "Service", "com.app", "Service.java")),
                List.of(new Reference("com.app.Service", "Mystery", ReferenceKind.TYPE_USE, "Service.java", 4)));

        ResolutionResult result = new SymbolResolver(List.of(service)).resolve();

        assertEquals(1, result.coverage().unresolved());
        assertTrue(result.coverage().examples().contains("Mystery"));
        assertTrue(result.coverage().plainSummary().contains("couldn't be pinned down"));
    }

    @Test
    void dropsSelfReferencesSoNoTypeDependsOnItself() {
        ParsedFile service = file("Service.java", "com.app",
                List.of(),
                List.of(type("com.app.Service", "Service", "com.app", "Service.java")),
                List.of(new Reference("com.app.Service", "Service", ReferenceKind.TYPE_USE, "Service.java", 6)));

        ResolutionResult result = new SymbolResolver(List.of(service)).resolve();

        assertTrue(result.references().isEmpty(), "a type referring to itself is not a dependency");
        assertEquals(1, result.coverage().internal(), "but it was still successfully identified");
    }

    @Test
    void coveragePercentageReflectsWhatWasFollowed() {
        Coverage coverage = new Coverage(10, 6, 2, 2, List.of("Mystery"));
        assertEquals(80.0, coverage.percentAccountedFor());
        assertFalse(coverage.plainSummary().isBlank());
    }
}
