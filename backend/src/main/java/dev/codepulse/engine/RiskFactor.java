package dev.codepulse.engine;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One scored factor with everything needed to re-derive its contribution by hand (schema
 * {@code risk_factor}, API {@code RiskFactor}). Storing raw value, thresholds, weight, and result
 * together is what makes the score explainable: nothing is hidden in code.
 *
 * @param normalizedValue {@code u(x)} in 0..1, 10 decimals, an explanatory approximation
 * @param contribution    {@code weight * u(x)}, 10 decimals, an explanatory approximation. The final
 *                        score is not the rounded sum of these; it is computed exactly and rounded once.
 * @param explanation     one safe sentence for the report; never contains source text
 */
public record RiskFactor(
    RiskFactorCode code,
    int rawValue,
    int lowThreshold,
    int highThreshold,
    BigDecimal normalizedValue,
    int weight,
    BigDecimal contribution,
    String explanation) {

    public RiskFactor {
        Objects.requireNonNull(code);
        Objects.requireNonNull(normalizedValue);
        Objects.requireNonNull(contribution);
        Objects.requireNonNull(explanation);
        if (rawValue < 0 || highThreshold <= lowThreshold || weight < 0 || weight > 100) {
            throw new IllegalArgumentException(code + ": invalid raw value, thresholds, or weight");
        }
        if (normalizedValue.signum() < 0 || normalizedValue.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(code + ": normalized value must be within 0..1");
        }
        if (contribution.signum() < 0 || contribution.compareTo(BigDecimal.valueOf(weight)) > 0) {
            throw new IllegalArgumentException(code + ": contribution must be within 0..weight");
        }
    }
}
