package dev.codepulse.engine;

import java.util.List;

/**
 * The engine's complete, immutable output for one workspace.
 *
 * @param inventory the whole-workspace file inventory (all files, not only Java)
 * @param javaFiles one analysis per eligible Java file, in path order
 */
public record AnalysisResult(InventoryResult inventory, List<JavaFileAnalysis> javaFiles) {

    public AnalysisResult {
        javaFiles = List.copyOf(javaFiles);
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
