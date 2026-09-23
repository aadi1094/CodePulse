package dev.codepulse.engine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixtures live in src/test/resources/fixtures/parse. They are plain text files that happen to
 * contain Java: Maven copies resources as-is and never compiles them.
 */
class JavaSourceParserTest {

    private final JavaSourceParser parser = new JavaSourceParser();

    @Test
    void validJava21SourceParses() throws IOException {
        ParseOutcome outcome = parser.parse(fixture("Java21Features.java"));

        assertEquals(ParseStatus.PARSED, outcome.status());
        assertTrue(outcome.diagnostics().isEmpty());
        assertTrue(outcome.syntaxTree().isPresent());
    }

    @Test
    void emptyFileParsesAsAnEmptyCompilationUnit() {
        ParseOutcome outcome = parser.parse("");

        assertEquals(ParseStatus.PARSED, outcome.status());
        assertTrue(outcome.syntaxTree().orElseThrow().getTypes().isEmpty());
    }

    @Test
    void missingBraceFailsEvenThoughTheParserRecoveredATree() throws IOException {
        ParseOutcome outcome = parser.parse(fixture("MissingBrace.java"));

        assertEquals(ParseStatus.PARSE_FAILED, outcome.status());
        assertFalse(outcome.syntaxTree().isPresent(), "a partial tree must never be exposed");
        assertEquals(DiagnosticCode.SYNTAX_ERROR, outcome.diagnostics().get(0).code());
    }

    @Test
    void diagnosticsNeverContainSourceText() throws IOException {
        ParseOutcome outcome = parser.parse(fixture("MissingBrace.java"));

        for (ParseDiagnostic diagnostic : outcome.diagnostics()) {
            assertFalse(diagnostic.toString().contains("passwordValue"));
            assertFalse(diagnostic.toString().contains("MissingBrace"));
        }
    }

    @Test
    void unnamedClassPreviewFeatureIsRejectedAsUnsupported() throws IOException {
        ParseOutcome outcome = parser.parse(fixture("UnnamedClass.java"));

        assertEquals(ParseStatus.PARSE_FAILED, outcome.status());
        assertEquals(List.of(new ParseDiagnostic(DiagnosticCode.UNSUPPORTED_SYNTAX, 3, 1)), outcome.diagnostics());
    }

    @Test
    void unnamedVariablePreviewFeatureIsRejected() throws IOException {
        ParseOutcome outcome = parser.parse(fixture("UnnamedVariable.java"));

        assertEquals(ParseStatus.PARSE_FAILED, outcome.status());
        // Line 6 is "        int _ = 42;" and the "_" is at column 13.
        assertEquals(List.of(new ParseDiagnostic(DiagnosticCode.SYNTAX_ERROR, 6, 13)), outcome.diagnostics());
    }

    @Test
    void diagnosticsAreCappedButTheTotalIsKept() {
        // Most syntax errors stop the parser at the first one, so they cannot test the cap.
        // Each "int _" is a separate problem, so 15 of them give exactly 15 problems.
        StringBuilder source = new StringBuilder("class A {\n  void m() {\n");  // lines 1-2
        for (int i = 1; i <= 15; i++) {
            source.append("    int _ = ").append(i).append(";\n");               // lines 3-17
        }
        source.append("  }\n}\n");

        ParseOutcome outcome = parser.parse(source.toString());

        assertEquals(ParseStatus.PARSE_FAILED, outcome.status());
        assertEquals(15, outcome.problemCount());
        assertEquals(10, outcome.diagnostics().size());
        // "    int _" puts "_" at column 9. Kept: lines 3 to 12.
        assertEquals(new ParseDiagnostic(DiagnosticCode.SYNTAX_ERROR, 3, 9), outcome.diagnostics().get(0));
        assertEquals(new ParseDiagnostic(DiagnosticCode.SYNTAX_ERROR, 12, 9), outcome.diagnostics().get(9));
    }

    private String fixture(String name) throws IOException {
        // Resources are found on the test classpath, so this works from Maven and from the IDE.
        try (InputStream in = getClass().getResourceAsStream("/fixtures/parse/" + name)) {
            assertNotNull(in, "missing fixture " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
