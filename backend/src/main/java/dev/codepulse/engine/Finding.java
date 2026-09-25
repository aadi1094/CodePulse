package dev.codepulse.engine;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * One rule-triggered note about a file (schema {@code finding}, API {@code Finding}).
 *
 * <p>Findings are separate from the score on purpose (Blueprint 10.2). The score is a smooth
 * function of measurements; a finding is a yes/no threshold with a line reference, so a reader can
 * jump to the place. Neither one secretly changes the other.
 *
 * @param beginLine first line of the evidence, or {@code null} for a whole-file finding
 * @param endLine   last line, present exactly when {@code beginLine} is
 * @param message   one safe sentence; never source code or raw comment text
 * @param evidence  typed measurements only (numbers, codes, identifiers), sorted by key
 */
public record Finding(
    String relativePath,
    String ruleCode,
    FindingSeverity severity,
    Integer beginLine,
    Integer endLine,
    String message,
    Map<String, Object> evidence) {

    public Finding {
        Objects.requireNonNull(relativePath);
        Objects.requireNonNull(ruleCode);
        Objects.requireNonNull(severity);
        Objects.requireNonNull(message);
        evidence = Map.copyOf(new TreeMap<>(evidence));
        if ((beginLine == null) != (endLine == null)) {
            throw new IllegalArgumentException(ruleCode + ": both line bounds or neither");
        }
        if (beginLine != null && (beginLine < 1 || endLine < beginLine)) {
            throw new IllegalArgumentException(ruleCode + ": invalid line range " + beginLine + "-" + endLine);
        }
        if (message.isBlank() || message.length() > 500) {
            throw new IllegalArgumentException(ruleCode + ": message must be 1..500 characters");
        }
    }
}
