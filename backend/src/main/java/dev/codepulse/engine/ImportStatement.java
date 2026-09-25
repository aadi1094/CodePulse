package dev.codepulse.engine;

/**
 * One {@code import} declaration exactly as written in a parsed file, before any resolution.
 *
 * <p>The parser gives us four facts and we keep all four. Which of them can become a graph edge is
 * decided later by {@link ImportGraphBuilder}, not here.
 *
 * @param name     the imported name without a trailing {@code .*}: {@code java.util.List},
 *                 {@code app.model.Order}; {@code java.util} for {@code import java.util.*;};
 *                 {@code java.util.Map.entry} for a static member import
 * @param isStatic {@code import static ...}
 * @param wildcard the declaration ends with {@code .*}
 * @param line     1-based line of the declaration
 */
public record ImportStatement(String name, boolean isStatic, boolean wildcard, int line) {

    public ImportStatement {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("import name must not be blank");
        }
        if (line < 1) {
            throw new IllegalArgumentException("line must be >= 1, was " + line);
        }
    }

    /** A plain {@code import a.b.C;}: the only kind {@code explicit-import-v1} can resolve. */
    public boolean isSingleType() {
        return !isStatic && !wildcard;
    }
}
