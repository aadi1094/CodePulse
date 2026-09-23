package dev.codepulse.engine;

/**
 * Work-marker counts found inside comments ({@code todoCount}, {@code fixmeCount}, Blueprint 9.3).
 * These are occurrences, not lines: "// TODO TODO" counts 2.
 */
public record CommentMarkers(int todoCount, int fixmeCount) {

    public CommentMarkers {
        if (todoCount < 0 || fixmeCount < 0) {
            throw new IllegalArgumentException("marker counts must be >= 0");
        }
    }
}
