package dev.codepulse.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Builds the {@code explicit-import-v1} graph from finished per-file results (Blueprint 9.6).
 *
 * <p>Two passes, both linear:
 * <ol>
 *   <li>Index: for every PARSED file, put each declared top-level type name into
 *       {@code Map<canonicalTypeName, Set<filePath>>}. Cost grows with the number of types.</li>
 *   <li>Resolve: for every single-type non-static import, one map lookup. Exactly one declaring file
 *       and it is not the importing file itself -> an edge. Cost grows with the number of imports.</li>
 * </ol>
 * Without the index, resolving would compare every import against every file: files x imports.
 *
 * <p>Pure function: same input list, same graph. No I/O, no parser, no mutable shared state.
 */
public final class ImportGraphBuilder {

    private ImportGraphBuilder() {
    }

    public static ImportGraph build(List<JavaFileAnalysis> files) {
        Map<String, TreeSet<String>> filesByTypeName = indexDeclaredTypes(files);

        Map<String, List<String>> ambiguousTypes = new TreeMap<>();
        for (Map.Entry<String, TreeSet<String>> entry : filesByTypeName.entrySet()) {
            if (entry.getValue().size() > 1) {
                ambiguousTypes.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }

        List<FileEdge> edges = new ArrayList<>();
        Map<String, ImportEvidence> evidenceByFile = new TreeMap<>();
        for (JavaFileAnalysis file : files) {
            if (file.parseStatus() == ParseStatus.PARSED) {
                evidenceByFile.put(file.relativePath(), resolveImports(file, filesByTypeName, edges));
            }
        }
        edges.sort(Comparator.comparing(FileEdge::sourcePath).thenComparing(FileEdge::targetPath));
        return new ImportGraph(edges, evidenceByFile, ambiguousTypes);
    }

    private static Map<String, TreeSet<String>> indexDeclaredTypes(List<JavaFileAnalysis> files) {
        Map<String, TreeSet<String>> index = new HashMap<>();
        for (JavaFileAnalysis file : files) {
            if (file.parseStatus() != ParseStatus.PARSED) {
                continue;                       // a failed file declares nothing we can trust
            }
            for (String typeName : file.declaredTypeNames()) {
                index.computeIfAbsent(typeName, k -> new TreeSet<>()).add(file.relativePath());
            }
        }
        return index;
    }

    /**
     * Sorts one file's imports into the five evidence buckets and appends its edges to {@code edges}.
     */
    private static ImportEvidence resolveImports(JavaFileAnalysis file, Map<String, TreeSet<String>> index,
                                                 List<FileEdge> edges) {
        int resolved = 0;
        int wildcard = 0;
        int statics = 0;
        int ambiguous = 0;
        int unresolved = 0;
        Set<String> distinctUnresolved = new LinkedHashSet<>();          // keeps first-seen order
        Map<String, EdgeAccumulator> byTarget = new TreeMap<>();

        for (ImportStatement statement : file.imports()) {
            if (statement.isStatic()) {
                statics++;                       // "import static a.B.*" is static, not wildcard
            } else if (statement.wildcard()) {
                wildcard++;
            } else {
                TreeSet<String> declaringFiles = index.get(statement.name());
                if (declaringFiles == null) {
                    unresolved++;
                    distinctUnresolved.add(statement.name());
                } else if (declaringFiles.size() > 1) {
                    ambiguous++;                 // do not pick the first; report instead
                } else {
                    resolved++;
                    String target = declaringFiles.first();
                    if (!target.equals(file.relativePath())) {       // ignore self-file edges
                        byTarget.computeIfAbsent(target, EdgeAccumulator::new).add(statement);
                    }
                }
            }
        }

        for (EdgeAccumulator accumulator : byTarget.values()) {
            edges.add(accumulator.toEdge(file.relativePath()));
        }
        List<String> examples = new ArrayList<>(distinctUnresolved).subList(0,
            Math.min(distinctUnresolved.size(), ImportEvidence.MAX_EXAMPLES));
        return new ImportEvidence(file.imports().size(), resolved, wildcard, statics, ambiguous, unresolved,
            examples, distinctUnresolved.size() > examples.size());
    }

    /** Collects every import from one source file to one target file, then collapses them into one edge. */
    private static final class EdgeAccumulator {

        private final String targetPath;
        private final TreeSet<String> importedTypes = new TreeSet<>();
        private final TreeSet<Integer> importLines = new TreeSet<>();

        EdgeAccumulator(String targetPath) {
            this.targetPath = targetPath;
        }

        void add(ImportStatement statement) {
            importedTypes.add(statement.name());
            importLines.add(statement.line());
        }

        FileEdge toEdge(String sourcePath) {
            List<String> types = firstItems(importedTypes);
            List<Integer> lines = firstItems(importLines);
            boolean truncated = types.size() < importedTypes.size() || lines.size() < importLines.size();
            return new FileEdge(sourcePath, targetPath, types, lines, truncated);
        }

        private static <T> List<T> firstItems(TreeSet<T> sorted) {
            List<T> kept = new ArrayList<>();
            for (T item : sorted) {
                if (kept.size() == FileEdge.MAX_EVIDENCE_ITEMS) {
                    break;
                }
                kept.add(item);
            }
            return kept;
        }
    }
}
