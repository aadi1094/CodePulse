package dev.codepulse.engine;

import com.github.javaparser.ast.CompilationUnit;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of parsing one file: PARSED with a syntax tree, or PARSE_FAILED with diagnostics.
 *
 * <p>This is a plain final class, not a record, on purpose. A record would publish the tree field
 * through an accessor that returns {@code null} for failed parses. Here the only way to reach the
 * tree is {@link #syntaxTree()}, which returns an {@link Optional}, so a caller cannot forget that a
 * failed parse has no tree.
 *
 * <p>The syntax tree is an engine-internal working object. It must never be stored, returned by an
 * API, or kept after the file's metrics are collected (Blueprint 9.1).
 */
public final class ParseOutcome {

    private final ParseStatus status;
    private final List<ParseDiagnostic> diagnostics;
    private final int problemCount;
    private final CompilationUnit syntaxTree; // null unless PARSED

    private ParseOutcome(ParseStatus status, List<ParseDiagnostic> diagnostics, int problemCount,
                         CompilationUnit syntaxTree) {
        this.status = status;
        this.diagnostics = List.copyOf(diagnostics);
        this.problemCount = problemCount;
        this.syntaxTree = syntaxTree;
    }

    static ParseOutcome parsed(CompilationUnit syntaxTree) {
        return new ParseOutcome(ParseStatus.PARSED, List.of(), 0, Objects.requireNonNull(syntaxTree));
    }

    /**
     * @param diagnostics  the kept diagnostics (may be fewer than problemCount if capped)
     * @param problemCount total number of problems found
     */
    static ParseOutcome failed(List<ParseDiagnostic> diagnostics, int problemCount) {
        if (problemCount < 1 || diagnostics.isEmpty() || diagnostics.size() > problemCount) {
            throw new IllegalArgumentException("a failed parse needs at least one diagnostic");
        }
        return new ParseOutcome(ParseStatus.PARSE_FAILED, diagnostics, problemCount, null);
    }

    public ParseStatus status() {
        return status;
    }

    public List<ParseDiagnostic> diagnostics() {
        return diagnostics;
    }

    /** @return total problems found; may exceed {@code diagnostics().size()} */
    public int problemCount() {
        return problemCount;
    }

    /** @return the tree for a PARSED file; empty for a failed one */
    public Optional<CompilationUnit> syntaxTree() {
        return Optional.ofNullable(syntaxTree);
    }
}
