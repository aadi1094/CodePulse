package dev.codepulse.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every expected score here was computed by hand from Blueprint 10.1:
 * u = clamp((x - low) / (high - low), 0, 1); score = roundHalfUp(sum of weight * u).
 */
class StructuralRiskPolicyTest {

    @TempDir
    Path root;

    // ---- the formula from plain numbers ----

    @Test
    void reproducesTheBlueprintPaymentServiceExampleExactly() {
        // complexity 24 -> 14/20 = 0.70 -> 35;  ncloc 700 -> 450/750 = 0.60 -> 12
        // maxMethodNcloc 88 -> 48/80 = 0.60 -> 6;  fanOut 11 -> 6/15 = 0.40 -> 4;  fanIn 8 -> 3/15 = 0.20 -> 2
        StructuralMeasurements paymentService = new StructuralMeasurements(24, 700, 88, 11, 8);

        assertEquals(59, StructuralRiskPolicy.score(paymentService));
        assertEquals(Priority.HIGH, StructuralRiskPolicy.bandFor(59));

        List<RiskFactor> factors = StructuralRiskPolicy.factors(paymentService);
        assertEquals(List.of(RiskFactorCode.MAX_COMPLEXITY, RiskFactorCode.FILE_NCLOC, RiskFactorCode.MAX_METHOD_NCLOC,
            RiskFactorCode.OBSERVED_FAN_OUT, RiskFactorCode.OBSERVED_FAN_IN), factors.stream().map(RiskFactor::code).toList());
        assertEquals(List.of("0.7", "0.6", "0.6", "0.4", "0.2"), plain(factors.stream().map(RiskFactor::normalizedValue).toList()));
        assertEquals(List.of("35", "12", "6", "4", "2"), plain(factors.stream().map(RiskFactor::contribution).toList()));
        RiskFactor complexity = factors.get(0);
        assertEquals(24, complexity.rawValue());
        assertEquals(10, complexity.lowThreshold());
        assertEquals(30, complexity.highThreshold());
        assertEquals(50, complexity.weight());
        assertEquals("Maximum method complexity is 24 (scored between 10 and 30); this contributes 35 of 50 points.",
            complexity.explanation());
    }

    @Test
    void factorsClampAtBothEnds() {
        assertEquals(0, StructuralRiskPolicy.score(new StructuralMeasurements(0, 0, 0, 0, 0)));
        assertEquals(0, StructuralRiskPolicy.score(new StructuralMeasurements(10, 250, 40, 5, 5)), "at low: still 0");
        assertEquals(100, StructuralRiskPolicy.score(new StructuralMeasurements(30, 1000, 120, 20, 20)), "at high: full weight");
        assertEquals(100, StructuralRiskPolicy.score(new StructuralMeasurements(999, 99999, 9999, 999, 999)), "beyond high: no extra");
    }

    @Test
    void everyBandBoundaryIsHit() {
        // 24 vs 25: complexity 19 -> 9/20*50 = 22.5;  fanOut 7 -> 2/15*10 = 1.333.. -> 23.83 -> 24
        //                                             fanOut 8 -> 3/15*10 = 2       -> 24.5  -> 25 (half up)
        assertEquals(24, StructuralRiskPolicy.score(new StructuralMeasurements(19, 0, 0, 7, 0)));
        assertEquals(25, StructuralRiskPolicy.score(new StructuralMeasurements(19, 0, 0, 8, 0)));
        // 49 vs 50: complexity 29 -> 47.5 + fanIn 7 -> 1.333.. = 48.83 -> 49;  complexity 30 -> 50
        assertEquals(49, StructuralRiskPolicy.score(new StructuralMeasurements(29, 0, 0, 0, 7)));
        assertEquals(50, StructuralRiskPolicy.score(new StructuralMeasurements(30, 0, 0, 0, 0)));
        // 74 vs 75: 50 + 20 + maxMethodNcloc 72 -> 32/80*10 = 4 -> 74;  80 -> 40/80*10 = 5 -> 75
        assertEquals(74, StructuralRiskPolicy.score(new StructuralMeasurements(30, 1000, 72, 0, 0)));
        assertEquals(75, StructuralRiskPolicy.score(new StructuralMeasurements(30, 1000, 80, 0, 0)));

        assertEquals(Priority.LOW, StructuralRiskPolicy.bandFor(0));
        assertEquals(Priority.LOW, StructuralRiskPolicy.bandFor(24));
        assertEquals(Priority.MODERATE, StructuralRiskPolicy.bandFor(25));
        assertEquals(Priority.MODERATE, StructuralRiskPolicy.bandFor(49));
        assertEquals(Priority.HIGH, StructuralRiskPolicy.bandFor(50));
        assertEquals(Priority.HIGH, StructuralRiskPolicy.bandFor(74));
        assertEquals(Priority.VERY_HIGH, StructuralRiskPolicy.bandFor(75));
        assertEquals(Priority.VERY_HIGH, StructuralRiskPolicy.bandFor(100));
        assertThrows(IllegalArgumentException.class, () -> StructuralRiskPolicy.bandFor(101));
    }

    @Test
    void halfPointsFromNonTerminatingFractionsRoundUpNotDown() {
        // complexity 11 -> 1/20*50 = 2.5;  ncloc 300 -> 50/750*20 = 1.3333...;  fanOut 6 -> 1/15*10 = 0.6666...
        // exact sum = 2.5 + 4/3 + 2/3 = 4.5 -> 5.  Adding rounded decimals could give 4.4999... -> 4.
        assertEquals(5, StructuralRiskPolicy.score(new StructuralMeasurements(11, 300, 0, 6, 0)));
        // Three terms of exactly 4/3 each: ncloc 300 -> 50/750*20;  fanOut 7 -> 2/15*10;  fanIn 7 -> 2/15*10.
        // exact sum = 2.5 + 4 = 6.5 -> 7.  Each 4/3 displays as 1.3333333333 (rounded DOWN), so adding the
        // displayed decimals gives 6.4999999999 -> 6. This case fails if the score is summed from decimals.
        assertEquals(7, StructuralRiskPolicy.score(new StructuralMeasurements(11, 300, 0, 7, 7)));
        // complexity 11 -> 2.5;  maxMethodNcloc 48 -> 8/80*10 = 1  ->  3.5 -> 4
        assertEquals(4, StructuralRiskPolicy.score(new StructuralMeasurements(11, 0, 48, 0, 0)));
        // and a plain below-half case still rounds down: complexity 11 -> 2.5 + fanIn 6 -> 0.666.. = 3.1666.. -> 3
        assertEquals(3, StructuralRiskPolicy.score(new StructuralMeasurements(11, 0, 0, 0, 6)));
    }

    @Test
    void scoreNeverDecreasesWhenAFactorGrows() {
        int previous = 0;
        for (int complexity = 0; complexity <= 40; complexity++) {
            int score = StructuralRiskPolicy.score(new StructuralMeasurements(complexity, 600, 60, 3, 9));
            assertTrue(score >= previous, "complexity " + complexity + " gave " + score + " < " + previous);
            previous = score;
        }
        previous = 0;
        for (int fanIn = 0; fanIn <= 25; fanIn++) {
            int score = StructuralRiskPolicy.score(new StructuralMeasurements(15, 0, 0, 0, fanIn));
            assertTrue(score >= previous);
            previous = score;
        }
    }

    @Test
    void policyIsFrozenUnderItsVersionName() {
        assertEquals("structural-v1", StructuralRiskPolicy.POLICY_VERSION);
        int totalWeight = 0;
        for (FactorDefinition definition : StructuralRiskPolicy.FACTORS) {
            totalWeight += definition.weight();
        }
        assertEquals(100, totalWeight, "weights add up to the 100-point scale");
        assertEquals(5, StructuralRiskPolicy.FACTORS.size());
    }

    // ---- wired into real files ----

    @Test
    void aParsedFileGetsFiveFactorsAndABand() throws IOException {
        write("src/main/java/app/Small.java", """
            package app;

            // TODO tidy up
            public class Small {
                int twice(int x) {
                    return x > 0 ? x * 2 : 0;    // complexity 2
                }
            }
            """);

        RiskAssessment small = analyze().assessment("src/main/java/app/Small.java");

        assertEquals(0, small.score(), "2 < 10, 6 < 250, 3 < 40, 0 < 5: everything below its low threshold");
        assertEquals(Priority.LOW, small.priority());
        assertEquals(5, small.factors().size());
        assertEquals(2, small.factor(RiskFactorCode.MAX_COMPLEXITY).rawValue());
        assertEquals(6, small.factor(RiskFactorCode.FILE_NCLOC).rawValue());
        assertEquals(3, small.factor(RiskFactorCode.MAX_METHOD_NCLOC).rawValue());
        assertEquals(0, small.factor(RiskFactorCode.OBSERVED_FAN_OUT).rawValue());
        assertEquals(0, small.factor(RiskFactorCode.OBSERVED_FAN_IN).rawValue());
        assertEquals("structural-v1", small.policyVersion());
        assertEquals("explicit-import-v1", small.graphMode());
        assertEquals(List.of(new AdditionalSignal("TODO_MARKER", 1)), small.additionalSignals());
        assertEquals(List.of(), small.priorityOverrides(), "overrides arrive in Slice 3C");
        assertEquals(2, small.limitations().size());
    }

    @Test
    void fanValuesComeFromTheImportGraph() throws IOException {
        write("src/main/java/app/Core.java", "package app;\npublic class Core {}\n");
        for (int i = 1; i <= 6; i++) {
            write("src/main/java/app/User" + i + ".java", "package app.u" + i + ";\nimport app.Core;\nclass User" + i + " {}\n");
        }

        RiskAssessment core = analyze().assessment("src/main/java/app/Core.java");

        // fanIn 6 -> 1/15 * 10 = 0.666.. -> rounds to 1
        assertEquals(6, core.factor(RiskFactorCode.OBSERVED_FAN_IN).rawValue());
        assertEquals("0.6666666667", core.factor(RiskFactorCode.OBSERVED_FAN_IN).contribution().toPlainString());
        assertEquals(1, core.score());
    }

    @Test
    void aFailedFileIsUnassessedNotLow() throws IOException {
        write("Broken.java", "class Broken {\n");
        write("Fine.java", "class Fine {}\n");

        AnalysisResult result = analyze();
        RiskAssessment broken = result.assessment("Broken.java");

        assertNull(broken.score(), "no score, never 0");
        assertEquals(Priority.UNASSESSED, broken.priority());
        assertTrue(broken.factors().isEmpty());
        assertTrue(broken.limitations().get(0).startsWith("File did not parse (SYNTAX_ERROR at line "));
        assertEquals(2, result.assessments().size());
        assertEquals("Broken.java", result.assessments().get(0).relativePath(), "same order as javaFiles");
        assertNull(result.assessment("README.md"));
    }

    @Test
    void aParsedFileWithoutBodiesExplainsItsZeroComplexity() throws IOException {
        write("Shape.java", "interface Shape { double area(); }\n");

        RiskAssessment shape = analyze().assessment("Shape.java");

        assertEquals(0, shape.factor(RiskFactorCode.MAX_COMPLEXITY).rawValue());
        assertEquals(3, shape.limitations().size());
        assertTrue(shape.limitations().get(2).contains("nothing was measurable"));
    }

    @Test
    void assessmentShapeIsAllOrNothing() {
        List<String> limits = List.of("x");
        assertThrows(IllegalArgumentException.class, () -> new RiskAssessment("A.java", "structural-v1",
            "explicit-import-v1", 10, Priority.UNASSESSED, List.of(), List.of(), List.of(), limits), "score without priority");
        assertThrows(IllegalArgumentException.class, () -> new RiskAssessment("A.java", "structural-v1",
            "explicit-import-v1", null, Priority.UNASSESSED, List.of(), List.of(), List.of(), List.of()), "no limitations");
        assertThrows(IllegalArgumentException.class, () -> new RiskFactor(RiskFactorCode.FILE_NCLOC, 5, 250, 1000,
            new BigDecimal("1.5"), 20, BigDecimal.ONE, "x"), "normalized above 1");
    }

    private static List<String> plain(List<BigDecimal> values) {
        return values.stream().map(v -> v.stripTrailingZeros().toPlainString()).toList();
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
