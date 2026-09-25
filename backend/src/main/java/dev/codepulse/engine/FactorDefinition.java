package dev.codepulse.engine;

import java.util.Objects;

/**
 * One row of a risk policy: which measurement, where its normalization starts and ends, and how
 * many of the 100 points it can contribute (Blueprint 10.1).
 *
 * <pre>
 *   u(x; low, high) = clamp((x - low) / (high - low), 0, 1)
 *   contribution    = weight * u
 * </pre>
 * Below {@code low} a factor contributes nothing; at or above {@code high} it contributes its full
 * weight; in between it rises in a straight line.
 */
public record FactorDefinition(RiskFactorCode code, int lowThreshold, int highThreshold, int weight) {

    public FactorDefinition {
        Objects.requireNonNull(code);
        if (highThreshold <= lowThreshold) {
            throw new IllegalArgumentException(code + ": high threshold must be above low");
        }
        if (weight < 0 || weight > 100) {
            throw new IllegalArgumentException(code + ": weight must be within 0..100");
        }
    }

    /** The width of the rising part of the line, {@code high - low}. */
    public int range() {
        return highThreshold - lowThreshold;
    }

    /** {@code x - low}, clamped into {@code 0..range}: the numerator of {@code u(x)}. */
    public int clampedExcess(int rawValue) {
        return Math.max(0, Math.min(range(), rawValue - lowThreshold));
    }
}
