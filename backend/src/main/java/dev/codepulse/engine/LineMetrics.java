package dev.codepulse.engine;

/**
 * Token-based line metrics for one parsed file ({@code java-metrics-v1}, Blueprint 9.3).
 * The three numbers may overlap, so they do not have to add up to the physical line count.
 *
 * @param ncloc        distinct lines touched by at least one code token (keyword, identifier,
 *                     literal, separator, operator). A line with code and a comment counts once.
 *                     A multi-line text block counts on every line it spans.
 * @param commentLines distinct lines touched by at least one comment token; may overlap ncloc
 * @param blankLines   lines containing only spaces, tabs, or form feeds (or nothing). Measured on the
 *                     raw text, so an empty line inside a comment or text block is also blank.
 */
public record LineMetrics(int ncloc, int commentLines, int blankLines) {

    public LineMetrics {
        if (ncloc < 0 || commentLines < 0 || blankLines < 0) {
            throw new IllegalArgumentException("line metrics must be >= 0");
        }
    }
}
