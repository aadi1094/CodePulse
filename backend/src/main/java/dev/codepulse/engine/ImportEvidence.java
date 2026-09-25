package dev.codepulse.engine;

import java.util.List;

/**
 * What happened to every import declaration of one parsed file ({@code import_evidence} in the
 * schema, {@code importEvidence} in the API). Kept even when no edge could be created, so the
 * report can say how much of the file's coupling the graph did NOT see (Blueprint 9.6).
 *
 * <p>Every import lands in exactly one bucket, so the five buckets add up to the total:
 * <ul>
 *   <li>static: {@code import static ...} (with or without {@code .*})</li>
 *   <li>wildcard: {@code import a.b.*;}</li>
 *   <li>resolved: single-type import of a type declared in exactly one parsed file of this
 *       snapshot (a self-import resolves but creates no edge)</li>
 *   <li>ambiguous: the type is declared in two or more parsed files; no edge, a warning instead</li>
 *   <li>unresolved or external: not in the index. Could be a library, the JDK, a nested type, or a
 *       type in a file that failed to parse; without type information we cannot tell</li>
 * </ul>
 *
 * @param unresolvedExamples distinct unresolved names in source order, at most {@link #MAX_EXAMPLES}
 * @param examplesTruncated  true when more distinct unresolved names existed than were kept
 */
public record ImportEvidence(
    int totalImportDeclarations,
    int resolvedSingleTypeImports,
    int wildcardImports,
    int staticImports,
    int ambiguousImports,
    int unresolvedOrExternalImports,
    List<String> unresolvedExamples,
    boolean examplesTruncated) {

    public static final int MAX_EXAMPLES = 20;

    /** A parsed file with no import declarations at all. */
    public static final ImportEvidence NONE = new ImportEvidence(0, 0, 0, 0, 0, 0, List.of(), false);

    public ImportEvidence {
        unresolvedExamples = List.copyOf(unresolvedExamples);
        if (totalImportDeclarations < 0 || resolvedSingleTypeImports < 0 || wildcardImports < 0
            || staticImports < 0 || ambiguousImports < 0 || unresolvedOrExternalImports < 0) {
            throw new IllegalArgumentException("import counts must be >= 0");
        }
        int sum = resolvedSingleTypeImports + wildcardImports + staticImports + ambiguousImports
            + unresolvedOrExternalImports;
        if (sum != totalImportDeclarations) {
            throw new IllegalArgumentException("buckets " + sum + " must add up to total " + totalImportDeclarations);
        }
        if (unresolvedExamples.size() > MAX_EXAMPLES || unresolvedExamples.size() > unresolvedOrExternalImports) {
            throw new IllegalArgumentException("too many unresolved examples: " + unresolvedExamples.size());
        }
        if (examplesTruncated && unresolvedExamples.size() != MAX_EXAMPLES) {
            throw new IllegalArgumentException("truncated evidence must keep exactly " + MAX_EXAMPLES + " examples");
        }
    }
}
