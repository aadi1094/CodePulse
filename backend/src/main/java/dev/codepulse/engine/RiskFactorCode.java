package dev.codepulse.engine;

/** The five measurements {@code structural-v1} scores (schema {@code risk_factor.factor_code}). */
public enum RiskFactorCode {
    MAX_COMPLEXITY,
    FILE_NCLOC,
    MAX_METHOD_NCLOC,
    OBSERVED_FAN_OUT,
    OBSERVED_FAN_IN
}
