package dev.codepulse.engine;

/** How loudly a finding should be shown (schema {@code finding.severity}). Not a priority: a file's priority comes from the score and overrides only. */
public enum FindingSeverity {
    INFO,
    WARNING,
    HIGH
}
