package dev.codepulse.engine;

import java.util.List;
import java.util.Objects;

/**
 * The engine's complete, immutable output for one workspace.
 *
 * @param inventory the whole-workspace file inventory (all files, not only Java)
 * @param javaFiles one analysis per eligible Java file, in path order
 * @param importGraph observed explicit imports between the parsed files ({@code explicit-import-v1})
 */
public record AnalysisResult(InventoryResult inventory, List<JavaFileAnalysis> javaFiles, ImportGraph importGraph) {

    public AnalysisResult {
        javaFiles = List.copyOf(javaFiles);
        Objects.requireNonNull(importGraph);
        // The graph's nodes must be exactly the parsed files: no more, no fewer.
        int parsed = 0;
        for (JavaFileAnalysis file : javaFiles) {
            if (file.parseStatus() == ParseStatus.PARSED) {
                parsed++;
                if (importGraph.evidence(file.relativePath()) == null) {
                    throw new IllegalArgumentException("parsed file missing from the import graph: " + file.relativePath());
                }
            }
        }
        if (importGraph.evidenceByFile().size() != parsed) {
            throw new IllegalArgumentException("import graph has nodes that are not parsed files");
        }
    }

    public int eligibleFileCount() {
        return javaFiles.size();
    }

    public int parsedFileCount() {
        int count = 0;
        for (JavaFileAnalysis file : javaFiles) {
            if (file.parseStatus() == ParseStatus.PARSED) {
                count++;
            }
        }
        return count;
    }

    public int parseFailedFileCount() {
        return eligibleFileCount() - parsedFileCount();
    }

    /**
     * @return parsed / eligible, e.g. 0.75; {@code null} when there are no eligible Java files,
     *         because "0 of 0" is not 0% and not 100%, it is unknown (Blueprint 9.2, API contract)
     */
    public Double parseCoverage() {
        if (eligibleFileCount() == 0) {
            return null;
        }
        return (double) parsedFileCount() / eligibleFileCount();
    }
}
