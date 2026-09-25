package dev.codepulse.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fixtures are generated so thresholds can be hit exactly; every expected line number is derived from the generator. */
class FindingRulesTest {

    @TempDir
    Path root;

    /** A method whose body has {@code branches} if-statements: complexity = 1 + branches. */
    private static String branchyMethod(String name, int branches) {
        StringBuilder sb = new StringBuilder("    int " + name + "(int x) {\n        int r = 0;\n");
        for (int i = 0; i < branches; i++) {
            sb.append("        if (x > ").append(i).append(") r++;\n");
        }
        return sb.append("        return r;\n    }\n").toString();
    }

    @Test
    void complexityRulesPointAtTheMethodAndExtremeReplacesHigh() throws IOException {
        // line 1 package, 2 blank, 3 class; mid = signature 4, r 5, 19 ifs 6..24, return 25, brace 26 -> complexity 20;
        // deep starts at 27 (29 ifs -> complexity 30)
        write("src/main/java/app/Branchy.java", "package app;\n\nclass Branchy {\n"
            + branchyMethod("mid", 19) + branchyMethod("deep", 29) + "}\n");

        List<Finding> findings = analyze().findings("src/main/java/app/Branchy.java");

        assertEquals(List.of("HIGH_METHOD_COMPLEXITY", "EXTREME_METHOD_COMPLEXITY"), codes(findings));
        Finding high = findings.get(0);
        assertEquals(FindingSeverity.WARNING, high.severity());
        assertEquals(4, high.beginLine());
        assertEquals(26, high.endLine());
        assertEquals(Map.of("ownerLabel", "Branchy", "signature", "mid(int)", "complexity", 20, "threshold", 20), high.evidence());
        assertEquals("mid(int) has complexity 20 (threshold 20).", high.message());
        Finding extreme = findings.get(1);
        assertEquals(FindingSeverity.HIGH, extreme.severity());
        assertEquals(27, extreme.beginLine());
        assertEquals(30, extreme.evidence().get("complexity"));
        assertEquals(30, extreme.evidence().get("threshold"));
    }

    @Test
    void justBelowTheComplexityThresholdIsSilent() throws IOException {
        write("Quiet.java", "class Quiet {\n" + branchyMethod("almost", 18) + "}\n");   // complexity 19

        assertEquals(List.of(), analyze().findings("Quiet.java"));
    }

    @Test
    void longMethodUsesNclocNotPhysicalLines() throws IOException {
        StringBuilder body = new StringBuilder("class Long {\n    void run() {\n        int r = 0;\n");
        for (int i = 0; i < 77; i++) {
            body.append("        r++;\n\n");                     // 77 code lines + 77 blank lines
        }
        body.append("    }\n}\n");                             // method ncloc: signature 1 + r 1 + 77 + brace 1 = 80
        write("Long.java", body.toString());

        List<Finding> findings = analyze().findings("Long.java");

        assertEquals(List.of("LONG_METHOD"), codes(findings));
        assertEquals(80, findings.get(0).evidence().get("ncloc"));
        assertEquals(2, findings.get(0).beginLine());
        assertEquals(FindingSeverity.WARNING, findings.get(0).severity());
    }

    @Test
    void largeFileBecomesVeryLargeAtTwoThousandLinesAndRaisesPriority() throws IOException {
        write("Large.java", classWithFields(998));           // 998 fields + class + brace = 1000 ncloc
        write("Huge.java", classWithFields(1998));           // 2000 ncloc

        AnalysisResult result = analyze();

        Finding large = result.findings("Large.java").get(0);
        assertEquals("LARGE_FILE", large.ruleCode());
        assertEquals(FindingSeverity.WARNING, large.severity());
        assertEquals(Map.of("ncloc", 1000, "threshold", 1000), large.evidence());
        assertNull(large.beginLine(), "whole-file finding");
        assertEquals(List.of("VERY_LARGE_FILE"), codes(result.findings("Huge.java")), "replaces LARGE, not in addition");
        assertEquals(FindingSeverity.HIGH, result.findings("Huge.java").get(0).severity());

        // The override: 2000 ncloc earns only 20 points (LOW band) but the priority floor is HIGH.
        RiskAssessment huge = result.assessment("Huge.java");
        assertEquals(20, huge.score(), "the score is not changed by the override");
        assertEquals(Priority.HIGH, huge.priority());
        assertEquals(List.of("MIN_HIGH_VERY_LARGE_FILE"), huge.priorityOverrides());
        assertEquals(Priority.LOW, result.assessment("Large.java").priority(), "1000 ncloc: 20 points, LOW band, no override");
    }

    @Test
    void markersAndUnresolvedImportsAreInformationalNotes() throws IOException {
        write("Notes.java", """
            package n;
            import java.util.List;
            import java.util.Map;
            // TODO one
            // todo two
            /* FIXME three */
            class Notes {}
            """);

        AnalysisResult result = analyze();
        List<Finding> findings = result.findings("Notes.java");

        assertEquals(List.of("FIXME_MARKER", "TODO_MARKER", "UNRESOLVED_IMPORTS"), codes(findings), "whole-file, by rule code");
        assertEquals(FindingSeverity.WARNING, findings.get(0).severity());
        assertEquals(Map.of("count", 1), findings.get(0).evidence());
        assertEquals(FindingSeverity.INFO, findings.get(1).severity());
        assertEquals(Map.of("count", 2), findings.get(1).evidence());
        assertEquals(Map.of("count", 2, "examples", List.of("java.util.List", "java.util.Map"), "examplesTruncated", false),
            findings.get(2).evidence());
        assertEquals(Priority.LOW, result.assessment("Notes.java").priority(), "markers never raise priority");
        for (Finding f : findings) {
            assertTrue(!f.message().contains("one") && !f.message().contains("three"), "no comment text leaks: " + f.message());
        }
    }

    @Test
    void failedFileGetsOnlyAParseFailedFinding() throws IOException {
        write("Broken.java", "class Broken {\n  // TODO never counted\n");

        List<Finding> findings = analyze().findings("Broken.java");

        assertEquals(1, findings.size());
        Finding f = findings.get(0);
        assertEquals("PARSE_FAILED", f.ruleCode());
        assertEquals(FindingSeverity.WARNING, f.severity());
        assertEquals("SYNTAX_ERROR", f.evidence().get("diagnosticCode"));
        assertEquals(1, f.evidence().get("diagnosticCount"));
        assertNull(f.beginLine());
    }

    @Test
    void findingsAcrossFilesAreSortedByPathThenLineThenRule() throws IOException {
        write("b/B.java", "class B {\n" + branchyMethod("deep", 29) + "}\n// TODO\n");
        write("a/A.java", "class A {\n" + branchyMethod("mid", 19) + "}\n");

        List<String> order = analyze().findings().stream()
            .map(f -> f.relativePath() + " " + f.ruleCode()).toList();

        assertEquals(List.of("a/A.java HIGH_METHOD_COMPLEXITY", "b/B.java TODO_MARKER", "b/B.java EXTREME_METHOD_COMPLEXITY"), order);
    }

    @Test
    void findingRecordGuardsItsShape() {
        assertThrows(IllegalArgumentException.class, () -> new Finding("A.java", "X", FindingSeverity.INFO, 3, null, "m", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Finding("A.java", "X", FindingSeverity.INFO, 5, 4, "m", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Finding("A.java", "X", FindingSeverity.INFO, null, null, " ", Map.of()));
    }

    private static String classWithFields(int count) {
        StringBuilder sb = new StringBuilder("class Big {\n");
        for (int i = 0; i < count; i++) {
            sb.append("    int f").append(i).append(";\n");
        }
        return sb.append("}\n").toString();
    }

    private static List<String> codes(List<Finding> findings) {
        return findings.stream().map(Finding::ruleCode).toList();
    }

    private AnalysisResult analyze() throws IOException {
        return new JavaSourceAnalyzer().analyze(new LocalDirectoryWorkspace(root));
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }
}
