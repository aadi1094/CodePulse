package dev.codepulse.engine;

import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Every expected number is copied from the "expected N" comment written above each method in
 * fixtures/complexity/Complexity.java. Those comments were written by hand before the code existed.
 */
class ComplexityCalculatorTest {

    private final JavaSourceParser parser = new JavaSourceParser();

    @Test
    void everyMethodInTheComplexityFixtureMatchesItsHandWrittenExpectation() throws IOException {
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("Complexity fee(boolean, int)", 5);
        expected.put("Complexity audit(int)", 1);
        expected.put("Complexity unknown()", null);
        expected.put("Complexity grade(int)", 4);
        expected.put("Complexity sign(int)", 3);
        expected.put("Complexity valid(boolean, boolean, boolean, boolean)", 4);
        expected.put("Complexity bits(int, int)", 1);
        expected.put("Complexity grouped(String)", 3);
        expected.put("Complexity oldSwitch(int)", 3);
        expected.put("Complexity describe(Object)", 5);
        expected.put("Complexity loops(List<Integer>)", 4);
        expected.put("Complexity parse(String)", 3);
        expected.put("Complexity lambda(boolean)", 2);
        expected.put("Complexity anonymous()", 1);
        expected.put("Complexity#anonymous@119 run()", 2);
        expected.put("Complexity anonymousWithArgument(boolean, boolean)", 2);
        expected.put("Complexity#anonymous@129 run()", 1);
        expected.put("Complexity local()", 1);
        expected.put("Complexity#Helper work()", 2);
        expected.put("Complexity Complexity(int)", 2);

        Map<String, Integer> actual = new LinkedHashMap<>();
        for (MethodMeasurement m : MethodCollector.collect(tree(fixture("Complexity.java")))) {
            actual.put(m.ownerLabel() + " " + m.signature(), m.complexity());
        }

        // Compare one by one first, so a failure names the exact method.
        for (Map.Entry<String, Integer> e : expected.entrySet()) {
            assertEquals(e.getValue(), actual.get(e.getKey()), e.getKey());
        }
        assertEquals(expected, actual, "no missing or extra methods");
    }

    @Test
    void theBlueprintFivePointExampleOnItsOwn() {
        String source = """
            class A {
                int fee(boolean premium, int units) {
                    if (premium && units > 10) return 0;
                    for (int i = 0; i < units; i++) {
                        if (i % 2 == 0) audit(i);
                    }
                    return 1;
                }
            }
            """;
        assertEquals(5, MethodCollector.collect(tree(source)).get(0).complexity());
    }

    @Test
    void maxMethodComplexityIsTheLargestAndIgnoresAbstractMethods() throws IOException {
        assertEquals(5, ComplexityCalculator.maxMethodComplexity(
            MethodCollector.collect(tree(fixture("Complexity.java")))));
    }

    @Test
    void maxMethodComplexityIsZeroWithNoExecutables() {
        // Only abstract methods: nothing measurable, so 0 (a PARSE_FAILED file would be null, not 0).
        List<MethodMeasurement> abstractOnly = MethodCollector.collect(
            tree("interface I { void a(); void b(); }"));
        assertEquals(0, ComplexityCalculator.maxMethodComplexity(abstractOnly));
        assertEquals(0, ComplexityCalculator.maxMethodComplexity(List.of()));
    }

    private CompilationUnit tree(String source) {
        return parser.parse(source).syntaxTree().orElseThrow();
    }

    private String fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/complexity/" + name)) {
            assertNotNull(in, "missing fixture " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
