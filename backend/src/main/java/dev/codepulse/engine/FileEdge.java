package dev.codepulse.engine;

import java.util.List;
import java.util.Objects;

/**
 * One directed edge {@code A -> B}: file A explicitly imports at least one type that is declared in
 * exactly one parsed file, B ({@code dependency_edge} in the schema, {@code DependencyEdge} in the
 * API, kind {@code EXPLICIT_IMPORT}).
 *
 * <p>Direction matters. {@code A -> B} says "A depends on B". Change B and A may be affected; change
 * A and B does not care. There is at most one edge per (A, B) pair: importing three types from B is
 * still one edge, with the three names kept as evidence.
 *
 * <p>This is observed evidence, not a complete dependency. A imports B says nothing about whether A
 * actually uses B, and same-package, wildcard, static, reflective, and injected dependencies are
 * not represented at all.
 *
 * @param importedTypes canonical names imported from B, sorted, at most {@link #MAX_EVIDENCE_ITEMS}
 * @param importLines   distinct lines in A holding those imports, ascending, at most
 *                      {@link #MAX_EVIDENCE_ITEMS}; a duplicate import shows as two lines
 * @param truncated     true when either list had to be cut
 */
public record FileEdge(
    String sourcePath,
    String targetPath,
    List<String> importedTypes,
    List<Integer> importLines,
    boolean truncated) {

    public static final int MAX_EVIDENCE_ITEMS = 20;

    public FileEdge {
        Objects.requireNonNull(sourcePath);
        Objects.requireNonNull(targetPath);
        importedTypes = List.copyOf(importedTypes);
        importLines = List.copyOf(importLines);
        if (sourcePath.equals(targetPath)) {
            throw new IllegalArgumentException("a file cannot depend on itself: " + sourcePath);
        }
        if (importedTypes.isEmpty() || importLines.isEmpty()) {
            throw new IllegalArgumentException("an edge needs at least one imported type and line");
        }
        if (importedTypes.size() > MAX_EVIDENCE_ITEMS || importLines.size() > MAX_EVIDENCE_ITEMS) {
            throw new IllegalArgumentException("edge evidence exceeds " + MAX_EVIDENCE_ITEMS + " items");
        }
    }
}
