package dev.codepulse.engine;

/**
 * Outcome of trying to parse one eligible Java file (Blueprint 9.2; {@code parse_status} in the schema).
 */
public enum ParseStatus {
    /** Parsed with zero problems. Only these files get structural metrics. */
    PARSED,
    /** The parser reported at least one problem, or the file used unsupported syntax. */
    PARSE_FAILED,
    /** Not attempted, e.g. because of a configured limit. Set by the caller, never by the parser. */
    SKIPPED
}
