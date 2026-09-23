package dev.codepulse.engine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static dev.codepulse.engine.DeclarationKind.COMPACT_CONSTRUCTOR;
import static dev.codepulse.engine.DeclarationKind.CONSTRUCTOR;
import static dev.codepulse.engine.DeclarationKind.METHOD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Expected rows were written by hand from the fixture's line numbers before running the code. */
class MethodCollectorTest {

    private final JavaSourceParser parser = new JavaSourceParser();

    @Test
    void everyMethodAndConstructorInTheDeclarationsFixture() throws IOException {
        // owner, signature, kind, hasBody, begin, end, ncloc
        List<MethodMeasurement> expected = List.of(
            m("Greeter", "greet(String)", METHOD, false, 13, 13, 1),
            m("Greeter", "greet()", METHOD, true, 15, 17, 3),
            m("Greeter", "polite()", METHOD, true, 19, 21, 3),
            m("Shop", "Shop()", CONSTRUCTOR, true, 27, 29, 3),
            m("Shop", "Shop(int)", CONSTRUCTOR, true, 31, 33, 3),
            m("Shop", "price(int)", METHOD, true, 35, 37, 3),
            m("Shop", "price(int, int)", METHOD, true, 39, 41, 3),      // overload kept separate
            m("Shop", "task()", METHOD, true, 43, 53, 11),              // includes the local/anonymous code
            m("Shop#Local", "run()", METHOD, true, 45, 46, 2),          // local class: "#"
            m("Shop#anonymous@48", "run()", METHOD, true, 49, 51, 3),   // anonymous class started on line 48
            m("Shop.Size#SMALL", "weight()", METHOD, true, 57, 59, 3),  // enum constant body
            m("Shop.Size", "weight()", METHOD, false, 62, 62, 1),       // member type: "."
            m("Shop.Item", "Item(String, int)", COMPACT_CONSTRUCTOR, true, 66, 70, 5));

        assertEquals(expected, MethodCollector.collect(tree(fixture("Declarations.java"))));
    }

    @Test
    void matchesTheDeclarationCountsForTheSameFile() throws IOException {
        var tree = tree(fixture("Declarations.java"));
        DeclarationCounts counts = DeclarationCounter.countIn(tree);

        assertEquals(counts.methodCount() + counts.constructorCount(), MethodCollector.collect(tree).size());
    }

    @Test
    void annotationsAreInsideTheRangeJavadocAndCommentsAreNotCode() {
        String source = """
            class Fmt {
                /** Javadoc sits above the method and is not part of its range. */
                @Deprecated
                String format(String pattern, Object... args) {
                    // a comment inside the body is not a code line
                    return pattern;
                }
                java.util.List<String> names(java.util.Map<String, Integer> byName) { return null; }
            }
            """;
        // format: lines 3-7; code lines 3,4,6,7 = 4. names: line 8 only.
        assertEquals(List.of(
                m("Fmt", "format(String, Object...)", METHOD, true, 3, 7, 4),
                // JavaParser prints types in a normalized form: no space after the comma.
                m("Fmt", "names(java.util.Map<String,Integer>)", METHOD, true, 8, 8, 1)),
            MethodCollector.collect(tree(source)));
    }

    @Test
    void signatureDoesNotChangeWhenTheCodeIsReformatted() {
        String tidy = "class A { void f(Map<String, Integer> x, int[] y) {} }";
        String messy = "class A { void f(Map< String ,/*c*/Integer > x, int [] y) {} }";

        String expected = "f(Map<String,Integer>, int[])";
        assertEquals(expected, MethodCollector.collect(tree(tidy)).get(0).signature());
        assertEquals(expected, MethodCollector.collect(tree(messy)).get(0).signature());
    }

    @Test
    void membersOfNestedAndLocalTypesGetDistinctOwners() {
        String source = """
            class A {
                class B {
                    void m() {
                        record R(int x) {
                            int twice() { return x * 2; }
                        }
                    }
                }
            }
            """;
        assertEquals(List.of(
                m("A.B", "m()", METHOD, true, 3, 7, 5),
                m("A.B#R", "twice()", METHOD, true, 5, 5, 1)),
            MethodCollector.collect(tree(source)));
    }

    @Test
    void aFileWithoutMethodsHasAnEmptyList() {
        assertEquals(List.of(), MethodCollector.collect(tree("record Point(int x, int y) {}\n")));
    }

    private static MethodMeasurement m(String owner, String signature, DeclarationKind kind,
                                       boolean hasBody, int begin, int end, int ncloc) {
        return new MethodMeasurement(owner, signature, kind, hasBody, begin, end, ncloc);
    }

    private com.github.javaparser.ast.CompilationUnit tree(String source) {
        return parser.parse(source).syntaxTree().orElseThrow();
    }

    private String fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/declarations/" + name)) {
            assertNotNull(in, "missing fixture " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
