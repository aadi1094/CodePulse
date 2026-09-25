package dev.codepulse.engine;

import java.util.List;
import java.util.Objects;

/**
 * The explainable review priority of one file (API {@code RiskExplanation}).
 *
 * <p>Two shapes, kept consistent by the constructor:
 * <ul>
 *   <li>assessed: score 0..100, priority LOW..VERY_HIGH, exactly one factor per policy row</li>
 *   <li>unassessed: score {@code null}, priority UNASSESSED, no factors, and a limitation saying why.
 *       A parse failure never becomes a low score; it becomes "no score".</li>
 * </ul>
 *
 * @param priorityOverrides codes of minimum-priority rules that fired (Blueprint 10.2); the numeric
 *                          score is never changed by them
 * @param additionalSignals counted observations that do not affect the score (TODO/FIXME)
 * @param limitations       what this assessment could not see; never empty
 */
public record RiskAssessment(
    String relativePath,
    String policyVersion,
    String graphMode,
    Integer score,
    Priority priority,
    List<RiskFactor> factors,
    List<String> priorityOverrides,
    List<AdditionalSignal> additionalSignals,
    List<String> limitations) {

    public RiskAssessment {
        Objects.requireNonNull(relativePath);
        Objects.requireNonNull(policyVersion);
        Objects.requireNonNull(graphMode);
        Objects.requireNonNull(priority);
        factors = List.copyOf(factors);
        priorityOverrides = List.copyOf(priorityOverrides);
        additionalSignals = List.copyOf(additionalSignals);
        limitations = List.copyOf(limitations);
        boolean assessed = score != null;
        if (assessed != (priority != Priority.UNASSESSED) || assessed == factors.isEmpty()) {
            throw new IllegalArgumentException("score, priority, and factors must all be present or all absent: " + relativePath);
        }
        if (assessed && (score < 0 || score > 100)) {
            throw new IllegalArgumentException("score must be within 0..100, was " + score);
        }
        if (!assessed && !priorityOverrides.isEmpty()) {
            throw new IllegalArgumentException("an unassessed file cannot have priority overrides");
        }
        if (limitations.isEmpty()) {
            throw new IllegalArgumentException("every assessment must state its limitations");
        }
    }

    /** @return the contribution of one factor, or null if unassessed or not a policy factor */
    public RiskFactor factor(RiskFactorCode code) {
        for (RiskFactor factor : factors) {
            if (factor.code() == code) {
                return factor;
            }
        }
        return null;
    }
}
