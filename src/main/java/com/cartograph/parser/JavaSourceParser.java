/*
 * JavaSourceParser.java
 * ---------------------
 * Purpose (plain English): Reads a Java file and reports what's inside it — the package, the
 * imports, every class and method it declares, and every other type it mentions. It uses
 * tree-sitter, which understands Java's shape without needing the code to compile, so a repo
 * with missing dependencies or errors still maps fine.
 */
package com.cartograph.parser;

import com.cartograph.model.ParsedFile;
import com.cartograph.model.Reference;
import com.cartograph.model.ReferenceKind;
import com.cartograph.model.Symbol;
import com.cartograph.model.SymbolKind;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterJava;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class JavaSourceParser implements Parser {

    private static final String LANGUAGE = "java";

    /**
     * tree-sitter parsers hold native state and are not safe to share between threads, so
     * each indexing thread gets its own. They are reused across files, which matters because
     * creating one is far more expensive than parsing with it.
     */
    private static final ThreadLocal<TSParser> PARSERS = ThreadLocal.withInitial(() -> {
        TSParser parser = new TSParser();
        parser.setLanguage(new TreeSitterJava());
        return parser;
    });

    /**
     * Names the grammar hands us as types that aren't ones. {@code var} is the big one: it
     * sits exactly where a type name would, but means "work it out from the right-hand side".
     */
    private static final Set<String> NOT_REAL_TYPES = Set.of("var");

    /** Grammar nodes that declare a type. Maps the node name onto our own vocabulary. */
    private static SymbolKind typeKindOf(String nodeType) {
        return switch (nodeType) {
            case "class_declaration" -> SymbolKind.CLASS;
            case "interface_declaration" -> SymbolKind.INTERFACE;
            case "enum_declaration" -> SymbolKind.ENUM;
            case "record_declaration" -> SymbolKind.RECORD;
            case "annotation_type_declaration" -> SymbolKind.ANNOTATION;
            default -> null;
        };
    }

    @Override
    public String language() {
        return LANGUAGE;
    }

    @Override
    public boolean handles(Path file) {
        return file.getFileName().toString().endsWith(".java");
    }

    @Override
    public ParsedFile parse(Path file, String relativePath) {
        byte[] raw;
        try {
            raw = Files.readAllBytes(file);
        } catch (IOException e) {
            return ParsedFile.empty(relativePath, LANGUAGE);
        }

        // Round-trip through String so the bytes we slice are exactly the bytes tree-sitter
        // indexed. A file with invalid UTF-8 would otherwise shift every offset.
        String text = new String(raw, StandardCharsets.UTF_8);
        byte[] source = text.getBytes(StandardCharsets.UTF_8);

        try {
            TSTree tree = PARSERS.get().parseString(null, text);
            return new FileScan(relativePath, source).run(tree.getRootNode());
        } catch (RuntimeException e) {
            // One unreadable file must never sink a whole indexing run.
            return ParsedFile.empty(relativePath, LANGUAGE);
        }
    }

    /**
     * Walks one file's syntax tree, accumulating what it finds. Kept as a short-lived
     * instance so the walk can carry state (the enclosing type, say) without passing a
     * long parameter list down every recursive call.
     */
    private static final class FileScan {

        private final String path;
        private final byte[] source;

        private final List<String> imports = new ArrayList<>();
        private final List<String> wildcardPackages = new ArrayList<>();
        private final List<Symbol> symbols = new ArrayList<>();
        private final List<Reference> references = new ArrayList<>();

        /** Generic placeholders like {@code T} or {@code K} — names, but not real types. */
        private final Set<String> typeParameters = new HashSet<>();

        /** Ids of the types we are currently inside, innermost last. */
        private final Deque<String> enclosingTypes = new ArrayDeque<>();

        private String packageName = "";
        private String primaryType;

        FileScan(String path, byte[] source) {
            this.path = path;
            this.source = source;
        }

        ParsedFile run(TSNode root) {
            visit(root);
            attributeImportsToPrimaryType();

            // Generic placeholders look exactly like type names at this level, so they are
            // filtered at the end, once every declaration in the file has been seen.
            List<Reference> real = references.stream()
                    .filter(r -> !typeParameters.contains(r.targetName()))
                    .toList();

            return new ParsedFile(path, LANGUAGE, packageName, imports, wildcardPackages, symbols, real);
        }

        private void visit(TSNode node) {
            String type = node.getType();

            switch (type) {
                case "package_declaration" -> {
                    packageName = textOfFirstNamedChild(node);
                    return;
                }
                case "import_declaration" -> {
                    recordImport(node);
                    return;
                }
                case "type_parameter" -> {
                    // The placeholder's own name is not a reference; its bounds still are.
                    typeParameters.add(textOfFirstNamedChild(node));
                    visitChildren(node);
                    return;
                }
                case "scoped_type_identifier" -> {
                    // A dotted type such as `Outer.Inner`. Treated as one name rather than
                    // recursed into, which would wrongly report `Outer` and `Inner` apart.
                    addReference(text(node), kindFor(node), node);
                    return;
                }
                case "type_identifier" -> {
                    addReference(text(node), kindFor(node), node);
                    return;
                }
                case "annotation", "marker_annotation" -> {
                    TSNode name = node.getChildByFieldName("name");
                    if (present(name)) {
                        addReference(text(name), ReferenceKind.ANNOTATION_USE, node);
                    }
                    visitChildren(node);
                    return;
                }
                case "method_invocation", "field_access" -> {
                    recordStaticAccess(node);
                    visitChildren(node);
                    return;
                }
                case "method_declaration", "constructor_declaration" -> {
                    recordMember(node, type.startsWith("method") ? SymbolKind.METHOD : SymbolKind.CONSTRUCTOR);
                    visitChildren(node);
                    return;
                }
                case "field_declaration" -> {
                    recordFields(node);
                    visitChildren(node);
                    return;
                }
                default -> {
                    SymbolKind kind = typeKindOf(type);
                    if (kind != null) {
                        visitTypeDeclaration(node, kind);
                        return;
                    }
                }
            }

            visitChildren(node);
        }

        private void visitChildren(TSNode node) {
            int count = node.getNamedChildCount();
            for (int i = 0; i < count; i++) {
                visit(node.getNamedChild(i));
            }
        }

        private void visitTypeDeclaration(TSNode node, SymbolKind kind) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (!present(nameNode)) {
                visitChildren(node);
                return;
            }

            String name = text(nameNode);
            // A type nested inside another is named `Outer.Inner`, matching how Java writes it.
            String id = enclosingTypes.isEmpty()
                    ? Symbol.qualify(packageName, name)
                    : enclosingTypes.peek() + "." + name;

            symbols.add(new Symbol(id, name, kind, packageName, path, startLine(node), endLine(node)));
            if (primaryType == null) {
                primaryType = id;
            }

            enclosingTypes.push(id);
            try {
                visitChildren(node);
            } finally {
                enclosingTypes.pop();
            }
        }

        private void recordMember(TSNode node, SymbolKind kind) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (!present(nameNode) || enclosingTypes.isEmpty()) {
                return;
            }
            String name = text(nameNode);
            symbols.add(new Symbol(
                    enclosingTypes.peek() + "#" + name,
                    name,
                    kind,
                    packageName,
                    path,
                    startLine(node),
                    endLine(node)));
        }

        private void recordFields(TSNode node) {
            if (enclosingTypes.isEmpty()) {
                return;
            }
            int count = node.getNamedChildCount();
            for (int i = 0; i < count; i++) {
                TSNode child = node.getNamedChild(i);
                if (!"variable_declarator".equals(child.getType())) {
                    continue;
                }
                TSNode nameNode = child.getChildByFieldName("name");
                if (!present(nameNode)) {
                    continue;
                }
                String name = text(nameNode);
                symbols.add(new Symbol(
                        enclosingTypes.peek() + "#" + name,
                        name,
                        SymbolKind.FIELD,
                        packageName,
                        path,
                        startLine(child),
                        endLine(child)));
            }
        }

        /**
         * Catches `Helper.doThing()` and `Config.DEFAULT`, where the receiver is a type name.
         * The grammar cannot tell a type from a variable here, so we fall back on Java's
         * near-universal convention that type names start with a capital letter. This is an
         * approximation and is documented as one.
         */
        private void recordStaticAccess(TSNode node) {
            TSNode receiver = node.getChildByFieldName("object");
            if (!present(receiver) || !"identifier".equals(receiver.getType())) {
                return;
            }
            String name = text(receiver);
            if (!name.isEmpty() && Character.isUpperCase(name.charAt(0)) && !looksLikeConstant(name)) {
                addReference(name, ReferenceKind.STATIC_ACCESS, receiver);
            }
        }

        /**
         * True for SCREAMING_SNAKE_CASE names. Java writes constants that way and types in
         * CamelCase, so this tells `CONFIG.get()` (a field) apart from `Config.get()` (a type).
         */
        private static boolean looksLikeConstant(String name) {
            return name.chars().noneMatch(Character::isLowerCase);
        }

        private void recordImport(TSNode node) {
            String statement = text(node);
            String body = statement
                    .replaceFirst("^import\\s+", "")
                    .replaceFirst("^static\\s+", "")
                    .replaceAll(";\\s*$", "")
                    .replaceAll("\\s+", "");
            boolean isStatic = statement.replaceFirst("^import\\s+", "").startsWith("static");

            if (body.endsWith(".*")) {
                String prefix = body.substring(0, body.length() - 2);
                // `import static Foo.*` pulls members off a type; `import foo.*` pulls in a
                // whole package. Only the latter is a package to search later.
                if (isStatic) {
                    imports.add(prefix);
                } else {
                    wildcardPackages.add(prefix);
                }
                return;
            }

            // A static import names a member, so the type is everything before the last dot.
            String target = isStatic ? stripLastSegment(body) : body;
            if (!target.isEmpty()) {
                imports.add(target);
            }
        }

        /**
         * Imports belong to the whole file, but the graph connects types. They are credited
         * to the file's first declared type, which for ordinary Java is the only one.
         */
        private void attributeImportsToPrimaryType() {
            if (primaryType == null) {
                return;
            }
            for (String imported : imports) {
                references.add(new Reference(primaryType, imported, ReferenceKind.IMPORT, path, 1));
            }
        }

        private void addReference(String targetName, ReferenceKind kind, TSNode at) {
            if (enclosingTypes.isEmpty() || targetName.isEmpty() || NOT_REAL_TYPES.contains(targetName)) {
                return;
            }
            references.add(new Reference(enclosingTypes.peek(), targetName, kind, path, startLine(at)));
        }

        /**
         * Works out how a type name is being used by looking at what encloses it, stopping at
         * the nearest declaration so we never read intent from an unrelated outer construct.
         */
        private ReferenceKind kindFor(TSNode node) {
            for (TSNode n = node.getParent(); present(n); n = n.getParent()) {
                switch (n.getType()) {
                    case "superclass":
                        return ReferenceKind.EXTENDS;
                    case "super_interfaces", "extends_interfaces":
                        return ReferenceKind.IMPLEMENTS;
                    case "object_creation_expression":
                        return ReferenceKind.INSTANTIATION;
                    case "annotation", "marker_annotation":
                        return ReferenceKind.ANNOTATION_USE;
                    case "class_declaration", "interface_declaration", "enum_declaration",
                         "record_declaration", "annotation_type_declaration",
                         "method_declaration", "constructor_declaration", "field_declaration",
                         "program":
                        return ReferenceKind.TYPE_USE;
                    default:
                        // Keep climbing through wrappers like generic_type or array_type.
                }
            }
            return ReferenceKind.TYPE_USE;
        }

        private static String stripLastSegment(String dotted) {
            int lastDot = dotted.lastIndexOf('.');
            return lastDot < 0 ? "" : dotted.substring(0, lastDot);
        }

        private String textOfFirstNamedChild(TSNode node) {
            return node.getNamedChildCount() == 0 ? "" : text(node.getNamedChild(0));
        }

        private String text(TSNode node) {
            int start = node.getStartByte();
            int end = node.getEndByte();
            if (start < 0 || end > source.length || end <= start) {
                return "";
            }
            return new String(source, start, end - start, StandardCharsets.UTF_8);
        }

        private static boolean present(TSNode node) {
            return node != null && !node.isNull();
        }

        private static int startLine(TSNode node) {
            return node.getStartPoint().getRow() + 1;
        }

        private static int endLine(TSNode node) {
            return node.getEndPoint().getRow() + 1;
        }
    }
}
