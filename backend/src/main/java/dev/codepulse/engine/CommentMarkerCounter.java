package dev.codepulse.engine;

import com.github.javaparser.JavaToken;
import com.github.javaparser.ast.CompilationUnit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Counts TODO and FIXME markers in comment tokens only (Blueprint 9.3 and 9.5).
 *
 * <p>Step 1 uses tokens to find comments, so strings and identifiers are never searched.
 * Step 2 uses a regular expression, but only on the text of one comment. The Blueprint allows
 * regex for that narrow job; it must never be used to find Java structure.
 *
 * <p>{@code \b} means "word boundary": the edge between a word character (letter, digit, or
 * underscore) and anything else. So {@code \bTODO\b} matches "TODO:" and "(todo)" but not
 * "TODOs" or "TODO_LATER". CASE_INSENSITIVE also matches "todo" and "Todo".
 */
public final class CommentMarkerCounter {

    // Compiled once and reused: a Pattern is immutable and safe to share.
    private static final Pattern TODO = Pattern.compile("\\bTODO\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIXME = Pattern.compile("\\bFIXME\\b", Pattern.CASE_INSENSITIVE);

    private CommentMarkerCounter() {
    }

    public static CommentMarkers count(CompilationUnit tree) {
        int todo = 0;
        int fixme = 0;
        for (JavaToken token : tree.getTokenRange()
                .orElseThrow(() -> new IllegalStateException("parser did not store tokens"))) {
            if (token.getCategory() == JavaToken.Category.COMMENT) {
                todo += occurrences(TODO, token.getText());
                fixme += occurrences(FIXME, token.getText());
            }
        }
        return new CommentMarkers(todo, fixme);
    }

    private static int occurrences(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {   // find() moves to the next match each time it is called
            count++;
        }
        return count;
    }
}
