package dev.codepulse.engine;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Groups file metrics by Java package and renders a deterministic, sorted text summary.
 *
 * <p>Deterministic output matters for CodePulse: the same input must always produce the same
 * report, regardless of the order files were discovered on disk. {@link TreeMap} and
 * {@link TreeSet} keep keys and values sorted, which removes insertion-order effects.
 */
public final class PackageSummary {

    private final List<FileMetrics> metrics;

    /**
     * @param metrics the files to summarize. The list is copied, so later changes by the caller
     *                cannot affect this summary (a "defensive copy").
     * @throws IllegalArgumentException if two entries share the same relative path
     * @throws NullPointerException     if the list or any element is null
     */
    public PackageSummary(List<FileMetrics> metrics) {
        this.metrics = List.copyOf(metrics);
        rejectDuplicatePaths(this.metrics);
    }

    private static void rejectDuplicatePaths(List<FileMetrics> metrics) {
        Set<String> seen = new HashSet<>();
        for (FileMetrics m : metrics) {
            // Set.add returns false when the element was already present.
            if (!seen.add(m.relativePath())) {
                throw new IllegalArgumentException("duplicate relativePath: " + m.relativePath());
            }
        }
    }

    /**
     * @return package name → sorted set of relative paths in that package; packages sorted by name.
     *         The default package appears under the empty string key.
     */
    public Map<String, Set<String>> groupByPackage() {
        Map<String, Set<String>> byPackage = new TreeMap<>();
        for (FileMetrics m : metrics) {
            Set<String> files = byPackage.get(m.packageName());
            if (files == null) {
                files = new TreeSet<>();
                byPackage.put(m.packageName(), files);
            }
            files.add(m.relativePath());
        }
        return byPackage;
    }

    /** @return a multi-line text report, one block per package, sorted. */
    public String render() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Set<String>> entry : groupByPackage().entrySet()) {
            String label = entry.getKey().isEmpty() ? "(default package)" : entry.getKey();
            out.append(label)
               .append(" (").append(entry.getValue().size()).append(" files)")
               .append('\n');
            for (String path : entry.getValue()) {
                FileMetrics m = findByPath(path);
                out.append("  ").append(path)
                   .append("  loc=").append(m.physicalLoc())
                   .append(" methods=").append(m.methodCount())
                   .append('\n');
            }
        }
        return out.toString();
    }

    private FileMetrics findByPath(String relativePath) {
        for (FileMetrics m : metrics) {
            if (m.relativePath().equals(relativePath)) {
                return m;
            }
        }
        // Cannot happen: every path in groupByPackage() came from this.metrics.
        throw new IllegalStateException("no metrics for path " + relativePath);
    }
}
