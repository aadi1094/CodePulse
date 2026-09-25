package dev.codepulse.engine;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The observed import graph of one snapshot, graph mode {@code explicit-import-v1} (Blueprint 9.6).
 *
 * <p>Nodes are the PARSED files (by relative path); edges are {@link FileEdge}s. The same edges are
 * stored twice, as two adjacency lists:
 * <pre>
 *   outgoing:  A -> {B, C}     "A imports B and C"          fan-out of A = 2
 *   incoming:  B -> {A}        "B is imported by A"         fan-in  of B = 1
 * </pre>
 * Two maps cost twice the memory of one, but make both questions ("what does A depend on?" and
 * "who depends on B?") a single map lookup instead of a scan over every edge.
 *
 * <p>Fan-in and fan-out are lower bounds: they count observed explicit imports only. A file that
 * failed to parse is not a node; its fan values are {@code null} (not measured), never 0.
 *
 * <p>Final class rather than a record so the derived adjacency maps can be built once in the
 * constructor and kept private.
 */
public final class ImportGraph {

    public static final String GRAPH_MODE = "explicit-import-v1";

    private final List<FileEdge> edges;
    private final Map<String, ImportEvidence> evidenceByFile;
    private final Map<String, List<String>> ambiguousTypes;
    private final Map<String, Set<String>> outgoing = new TreeMap<>();
    private final Map<String, Set<String>> incoming = new TreeMap<>();

    /**
     * @param edges          unique directed edges, sorted by source then target
     * @param evidenceByFile one entry per PARSED file, including files with zero imports
     * @param ambiguousTypes canonical type name -> the two or more parsed files declaring it
     * @throws IllegalArgumentException if an edge touches a file that is not a parsed node
     */
    ImportGraph(List<FileEdge> edges, Map<String, ImportEvidence> evidenceByFile,
                Map<String, List<String>> ambiguousTypes) {
        this.edges = List.copyOf(edges);
        this.evidenceByFile = Collections.unmodifiableMap(new TreeMap<>(evidenceByFile));
        Map<String, List<String>> ambiguous = new TreeMap<>();
        ambiguousTypes.forEach((name, files) -> ambiguous.put(name, List.copyOf(files)));
        this.ambiguousTypes = Collections.unmodifiableMap(ambiguous);

        for (FileEdge edge : this.edges) {
            requireNode(edge.sourcePath());
            requireNode(edge.targetPath());
            boolean added = outgoing.computeIfAbsent(edge.sourcePath(), k -> new TreeSet<>()).add(edge.targetPath());
            incoming.computeIfAbsent(edge.targetPath(), k -> new TreeSet<>()).add(edge.sourcePath());
            if (!added) {
                throw new IllegalArgumentException("duplicate edge " + edge.sourcePath() + " -> " + edge.targetPath());
            }
        }
    }

    /** The graph of a snapshot with no parsed files. */
    public static ImportGraph empty() {
        return new ImportGraph(List.of(), Map.of(), Map.of());
    }

    private void requireNode(String path) {
        if (!evidenceByFile.containsKey(path)) {
            throw new IllegalArgumentException("edge endpoint is not a parsed file: " + path);
        }
    }

    public String graphMode() {
        return GRAPH_MODE;
    }

    public List<FileEdge> edges() {
        return edges;
    }

    public int edgeCount() {
        return edges.size();
    }

    /** Parsed files (the nodes) in path order, each with its import evidence. */
    public Map<String, ImportEvidence> evidenceByFile() {
        return evidenceByFile;
    }

    /** @return the file's import evidence, or {@code null} if the file is not a parsed node */
    public ImportEvidence evidence(String path) {
        return evidenceByFile.get(path);
    }

    /** Type names declared in more than one parsed file. Imports of them create no edge. */
    public Map<String, List<String>> ambiguousTypes() {
        return ambiguousTypes;
    }

    /** Files that {@code path} explicitly imports, sorted; empty when none or when not a node. */
    public Set<String> importedFiles(String path) {
        return Collections.unmodifiableSet(outgoing.getOrDefault(path, Collections.emptySortedSet()));
    }

    /** Files that explicitly import {@code path}, sorted; empty when none or when not a node. */
    public Set<String> importingFiles(String path) {
        return Collections.unmodifiableSet(incoming.getOrDefault(path, Collections.emptySortedSet()));
    }

    /** @return number of distinct files {@code path} imports; {@code null} if the file is not a parsed node */
    public Integer observedFanOut(String path) {
        return evidenceByFile.containsKey(path) ? importedFiles(path).size() : null;
    }

    /** @return number of distinct files importing {@code path}; {@code null} if the file is not a parsed node */
    public Integer observedFanIn(String path) {
        return evidenceByFile.containsKey(path) ? importingFiles(path).size() : null;
    }
}
