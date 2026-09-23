package dev.codepulse.engine;

/** Why a parse failed. Stored instead of the parser's raw message, which can quote source code. */
public enum DiagnosticCode {
    /** JavaParser reported a problem: broken syntax, or a feature its JAVA_21 check rejects. */
    SYNTAX_ERROR,
    /** Parsed, but uses syntax outside our contract (Java 21 without previews), e.g. an unnamed class. */
    UNSUPPORTED_SYNTAX,
    /** The file's bytes are not valid UTF-8, so it was never handed to the parser. */
    ENCODING_ERROR
}
