package dev.codepulse.engine;

import java.util.Objects;

/**
 * One sanitized parse problem: a code and a position, never the parser's message text.
 * The raw message can contain identifiers or string contents from the analyzed file.
 *
 * @param code   what kind of problem
 * @param line   1-based line, or {@code null} when the parser gave no position (unknown, not 0)
 * @param column 1-based column, or {@code null} when unknown
 */
public record ParseDiagnostic(DiagnosticCode code, Integer line, Integer column) {

    public ParseDiagnostic {
        Objects.requireNonNull(code, "code must not be null");
        if ((line == null) != (column == null)) {
            throw new IllegalArgumentException("line and column must both be known or both be null");
        }
        if (line != null && (line < 1 || column < 1)) {
            throw new IllegalArgumentException("line and column are 1-based");
        }
    }
}
