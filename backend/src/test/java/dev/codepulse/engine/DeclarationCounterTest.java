package dev.codepulse.engine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Expected numbers come from the hand tallies written in each fixture file, not from running the code.
 * Argument order: class, interface, enum, record, annotation, anonymous,
 *                 method, constructor, executable, lambda.
 */
class DeclarationCounterTest {

    private final JavaSourceParser parser = new JavaSourceParser();

    @Test
    void countsEveryKindOfDeclarationInTheMixedFixture() throws IOException {
        assertEquals(
            new DeclarationCounts(2, 1, 1, 1, 1, 1, 10, 3, 11, 2),
            countsFor(fixture("Declarations.java")));
    }

    @Test
    void implicitMembersAreNeverCounted() throws IOException {
        assertEquals(
            new DeclarationCounts(1, 0, 1, 1, 0, 0, 0, 0, 0, 0),
            countsFor(fixture("ImplicitMembers.java")));
    }

    @Test
    void emptyFileHasAllZeroCounts() {
        assertEquals(new DeclarationCounts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0), countsFor(""));
    }

    @Test
    void interfaceMethodsCountButOnlyThoseWithBodiesAreExecutable() {
        String source = """
            interface Api {
                void a();
                void b();
                default void c() {}
            }
            """;
        assertEquals(new DeclarationCounts(0, 1, 0, 0, 0, 0, 3, 0, 1, 0), countsFor(source));
    }

    @Test
    void nestedTypesInsideNestedTypesAreEachCountedOnce() {
        String source = """
            class Outer {
                class Middle {
                    class Inner {
                        void deep() {}
                    }
                }
            }
            """;
        assertEquals(new DeclarationCounts(3, 0, 0, 0, 0, 0, 1, 0, 1, 0), countsFor(source));
    }

    @Test
    void aBrokenFileGetsNoCountsAtAllNotSmallerCounts() {
        // Half of a class: the parser stops early. There must be NO counts, not "1 method".
        String broken = "class Half { void one() {} void two() { ";

        ParseOutcome outcome = parser.parse(broken);
        Optional<DeclarationCounts> counts = outcome.syntaxTree().map(DeclarationCounter::countIn);

        assertEquals(ParseStatus.PARSE_FAILED, outcome.status());
        assertTrue(counts.isEmpty(), "failed parse = not measured, never a partial count");
    }

    private DeclarationCounts countsFor(String source) {
        return DeclarationCounter.countIn(parser.parse(source).syntaxTree().orElseThrow());
    }

    private String fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/declarations/" + name)) {
            assertNotNull(in, "missing fixture " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
