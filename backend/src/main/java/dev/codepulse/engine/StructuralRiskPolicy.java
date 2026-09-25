package dev.codepulse.engine;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code structural-v1} attention score (Blueprint 10.1-10.3).
 *
 * <pre>
 *   score = roundHalfUp(  50 * u(maxMethodComplexity; 10, 30)
 *                       + 20 * u(fileNcloc;           250, 1000)
 *                       + 10 * u(maxMethodNcloc;       40, 120)
 *                       + 10 * u(observedFanOut;        5, 20)
 *                       + 10 * u(observedFanIn;         5, 20) )
 * </pre>
 *
 * <p>This is a heuristic ordering aid, not a probability of bugs. Weights and thresholds are project
 * hypotheses frozen under one version name; changing any of them means a new version, never a
 * silent edit.
 *
 * <p><b>Why the final rounding is exact.</b> A contribution such as {@code 20 * 50/750} is a fraction
 * that never terminates. Summing five such decimals (or doubles) can give {@code 4.4999999...} where
 * the true sum is exactly {@code 4.5}, and half-up rounding would then go the wrong way. So the
 * score is computed as one exact integer numerator over a common denominator and rounded once.
 * The per-factor decimals in {@link RiskFactor} are for reading, not for adding up.
 *
 * <p>Pure: same measurements, same score. No I/O, no state.
 */
public final class StructuralRiskPolicy {

    public static final String POLICY_VERSION = "structural-v1";

    /** Minimum-priority overrides are Slice 3C; this slice computes bands only. */
    public static final List<FactorDefinition> FACTORS = List.of(
        new FactorDefinition(RiskFactorCode.MAX_COMPLEXITY, 10, 30, 50),
        new FactorDefinition(RiskFactorCode.FILE_NCLOC, 250, 1000, 20),
        new FactorDefinition(RiskFactorCode.MAX_METHOD_NCLOC, 40, 120, 10),
        new FactorDefinition(RiskFactorCode.OBSERVED_FAN_OUT, 5, 20, 10),
        new FactorDefinition(RiskFactorCode.OBSERVED_FAN_IN, 5, 20, 10));

    static final int DISPLAY_SCALE = 10;

    static final String IMPORT_GRAPH_LIMITATION = "Import analysis counts explicit single-type imports only; "
        + "same-package references, wildcard and static imports, reflection, and dependency injection are not observed.";
    static final String HISTORY_LIMITATION = "Git history was not collected; change activity is unknown, not zero.";

    private StructuralRiskPolicy() {
    }

    /** Scores one eligible file. A file that did not parse gets no score, not a low one. */
    public static RiskAssessment assess(JavaFileAnalysis file, ImportGraph graph) {
        if (file.parseStatus() != ParseStatus.PARSED) {
            return new RiskAssessment(file.relativePath(), POLICY_VERSION, graph.graphMode(), null,
                Priority.UNASSESSED, List.of(), List.of(), List.of(),
                List.of(unparsedReason(file), IMPORT_GRAPH_LIMITATION, HISTORY_LIMITATION));
        }
        StructuralMeasurements measurements = StructuralMeasurements.of(file, graph);
        List<RiskFactor> factors = factors(measurements);
        int score = score(measurements);
        List<String> limitations = new ArrayList<>(List.of(IMPORT_GRAPH_LIMITATION, HISTORY_LIMITATION));
        if (file.declarations().executableCount() == 0) {
            limitations.add("No method or constructor bodies; maximum complexity is 0 because nothing was measurable, not because code is simple.");
        }
        return new RiskAssessment(file.relativePath(), POLICY_VERSION, graph.graphMode(), score,
            bandFor(score), factors, List.of(), signals(file.markers()), limitations);
    }

    /** {@code u(x; low, high)} as a 10-decimal approximation of the exact fraction. */
    static BigDecimal normalize(int rawValue, FactorDefinition definition) {
        return BigDecimal.valueOf(definition.clampedExcess(rawValue))
            .divide(BigDecimal.valueOf(definition.range()), DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    /** The five explanatory factor rows, in policy order. */
    static List<RiskFactor> factors(StructuralMeasurements measurements) {
        List<RiskFactor> factors = new ArrayList<>();
        for (FactorDefinition definition : FACTORS) {
            int raw = measurements.value(definition.code());
            BigDecimal contribution = BigDecimal.valueOf((long) definition.weight() * definition.clampedExcess(raw))
                .divide(BigDecimal.valueOf(definition.range()), DISPLAY_SCALE, RoundingMode.HALF_UP);
            factors.add(new RiskFactor(definition.code(), raw, definition.lowThreshold(), definition.highThreshold(),
                normalize(raw, definition), definition.weight(), contribution,
                explain(definition, raw, contribution)));
        }
        return List.copyOf(factors);
    }

    /**
     * The exact score, rounded half up once. Every term {@code weight * excess / range} is scaled to
     * the common denominator {@code L = lcm(all ranges)} so the sum is an exact integer over L.
     */
    static int score(StructuralMeasurements measurements) {
        BigInteger commonDenominator = BigInteger.ONE;
        for (FactorDefinition definition : FACTORS) {
            BigInteger range = BigInteger.valueOf(definition.range());
            commonDenominator = commonDenominator.multiply(range).divide(commonDenominator.gcd(range));   // lcm
        }
        BigInteger numerator = BigInteger.ZERO;
        for (FactorDefinition definition : FACTORS) {
            BigInteger scale = commonDenominator.divide(BigInteger.valueOf(definition.range()));
            long term = (long) definition.weight() * definition.clampedExcess(measurements.value(definition.code()));
            numerator = numerator.add(BigInteger.valueOf(term).multiply(scale));
        }
        return new BigDecimal(numerator).divide(new BigDecimal(commonDenominator), 0, RoundingMode.HALF_UP).intValueExact();
    }

    /** Blueprint 10.2 base bands. Overrides (Slice 3C) may raise the priority, never the score. */
    static Priority bandFor(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be within 0..100, was " + score);
        }
        if (score >= 75) {
            return Priority.VERY_HIGH;
        }
        if (score >= 50) {
            return Priority.HIGH;
        }
        if (score >= 25) {
            return Priority.MODERATE;
        }
        return Priority.LOW;
    }

    private static List<AdditionalSignal> signals(CommentMarkers markers) {
        List<AdditionalSignal> signals = new ArrayList<>();
        if (markers.todoCount() > 0) {
            signals.add(new AdditionalSignal("TODO_MARKER", markers.todoCount()));
        }
        if (markers.fixmeCount() > 0) {
            signals.add(new AdditionalSignal("FIXME_MARKER", markers.fixmeCount()));
        }
        return signals;
    }

    private static String explain(FactorDefinition definition, int raw, BigDecimal contribution) {
        String what = switch (definition.code()) {
            case MAX_COMPLEXITY -> "Maximum method complexity";
            case FILE_NCLOC -> "File source lines (ncloc)";
            case MAX_METHOD_NCLOC -> "Longest method source lines";
            case OBSERVED_FAN_OUT -> "Observed files imported";
            case OBSERVED_FAN_IN -> "Observed importing files";
        };
        return what + " is " + raw + " (scored between " + definition.lowThreshold() + " and "
            + definition.highThreshold() + "); this contributes " + contribution.stripTrailingZeros().toPlainString()
            + " of " + definition.weight() + " points.";
    }

    private static String unparsedReason(JavaFileAnalysis file) {
        ParseDiagnostic first = file.diagnostics().get(0);
        return "File did not parse (" + first.code() + (first.line() == null ? "" : " at line " + first.line())
            + "); no structural metrics were measured, so no score exists.";
    }
}
