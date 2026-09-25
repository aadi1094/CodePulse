package dev.codepulse.engine;

import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Expectations are hand-written from the line comments in {@code fixtures/imports/ShopImports.java}. */
class ImportCollectorTest {

    private final JavaSourceParser parser = new JavaSourceParser();

    @Test
    void keepsEveryImportAsWrittenWithItsKindAndLine() throws IOException {
        CompilationUnit tree = parse(fixture("ShopImports.java"));

        assertEquals(List.of(
            new ImportStatement("java.util.List", false, false, 3),
            new ImportStatement("java.util", false, true, 4),
            new ImportStatement("java.util.Map.entry", true, false, 5),
            new ImportStatement("java.util.Collections", true, true, 6),
            new ImportStatement("shop.model.Order", false, false, 7),
            new ImportStatement("shop.model.Order.Line", false, false, 8),
            new ImportStatement("shop.model.Order", false, false, 9)),   // duplicate is kept
            ImportCollector.imports(tree));
    }

    @Test
    void onlyPlainImportsAreSingleType() {
        assertTrue(new ImportStatement("a.B", false, false, 1).isSingleType());
        assertFalse(new ImportStatement("a", false, true, 1).isSingleType());
        assertFalse(new ImportStatement("a.B.c", true, false, 1).isSingleType());
    }

    @Test
    void declaredTypeNamesAreTopLevelOnlyAndFullyQualified() throws IOException {
        CompilationUnit tree = parse(fixture("ShopImports.java"));

        // Shop.Inner is a member type: not a top-level import target in explicit-import-v1.
        assertEquals(List.of("shop.Shop", "shop.Helper", "shop.Kind", "shop.Money", "shop.Tag"),
            ImportCollector.declaredTypeNames(tree));
    }

    @Test
    void defaultPackageTypesHaveNoPrefix() {
        CompilationUnit tree = parse("class A {}\ninterface B {}\n");

        assertEquals(List.of("A", "B"), ImportCollector.declaredTypeNames(tree));
        assertEquals(List.of(), ImportCollector.imports(tree));
    }

    @Test
    void emptyFileDeclaresAndImportsNothing() {
        CompilationUnit tree = parse("");

        assertEquals(List.of(), ImportCollector.declaredTypeNames(tree));
        assertEquals(List.of(), ImportCollector.imports(tree));
    }

    private CompilationUnit parse(String source) {
        return parser.parse(source).syntaxTree().orElseThrow();
    }

    private String fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/imports/" + name)) {
            if (in == null) {
                throw new IllegalStateException("missing fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
