package dev.codepulse.engine;

import com.github.javaparser.JavaToken;
import com.github.javaparser.Range;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Calculates {@link LineMetrics} from a parsed tree's tokens plus the original text.
 *
 * <p>Why tokens instead of searching the text for "//"? The tokenizer already knows that
 * {@code "http://example.com"} is one string literal, so the {@code //} inside it is not a comment.
 * A text search would be fooled.
 *
 * <p>A Set of line numbers gives "distinct lines" for free: adding line 7 twice keeps one 7.
 */
public final class LineMetricsCalculator {

    private LineMetricsCalculator() {
    }

    /**
     * @param tree       a successfully parsed tree (tokens are stored by JavaParser's default config)
     * @param sourceText the exact text that was parsed
     */
    public static LineMetrics calculate(CompilationUnit tree, String sourceText) {
        TokenRange tokens = tree.getTokenRange()
            .orElseThrow(() -> new IllegalStateException("parser did not store tokens"));

        Set<Integer> codeLines = new HashSet<>();
        Set<Integer> commentLines = new HashSet<>();
        for (JavaToken token : tokens) {
            Set<Integer> target = null;                // spaces and line breaks count nowhere
            if (isCodeToken(token)) {
                target = codeLines;
            } else if (token.getCategory() == JavaToken.Category.COMMENT) {
                target = commentLines;
            }
            Optional<Range> range = token.getRange();
            if (target != null && range.isPresent()) {
                addLines(target, range.get().begin.line, range.get().end.line);
            }
        }
        return new LineMetrics(codeLines.size(), commentLines.size(), countBlankLines(sourceText));
    }

    /** Code tokens: keyword, identifier, literal, separator, operator. Not comments or whitespace. */
    static boolean isCodeToken(JavaToken token) {
        return switch (token.getCategory()) {
            case KEYWORD, IDENTIFIER, LITERAL, SEPARATOR, OPERATOR -> true;
            case COMMENT, WHITESPACE_NO_EOL, EOL -> false;
        };
    }

    /** Distinct lines touched by code tokens inside one token range (e.g. one method). */
    static int codeLinesIn(TokenRange tokens) {
        Set<Integer> lines = new HashSet<>();
        for (JavaToken token : tokens) {
            Optional<Range> range = token.getRange();
            if (isCodeToken(token) && range.isPresent()) {
                addLines(lines, range.get().begin.line, range.get().end.line);
            }
        }
        return lines.size();
    }

    private static void addLines(Set<Integer> lines, int firstLine, int lastLine) {
        for (int line = firstLine; line <= lastLine; line++) {
            lines.add(line);
        }
    }

    /**
     * Counts whitespace-only lines using the same line rules as {@link PhysicalLineCounter}:
     * \r\n, \n, or a lone \r ends a line, and a final line break does not start an extra line.
     */
    static int countBlankLines(String text) {
        int blank = 0;
        boolean lineHasOnlyWhitespace = true;
        boolean lineStarted = false;   // any character seen since the last line break?
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n') {
                if (lineHasOnlyWhitespace) {
                    blank++;
                }
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;                // \r\n is one line break
                }
                lineHasOnlyWhitespace = true;
                lineStarted = false;
            } else {
                lineStarted = true;
                if (c != ' ' && c != '\t' && c != '\f') {   // Java's whitespace (JLS 3.6)
                    lineHasOnlyWhitespace = false;
                }
            }
        }
        if (lineStarted && lineHasOnlyWhitespace) {
            blank++;                    // last line had no line break after it
        }
        return blank;
    }
}
