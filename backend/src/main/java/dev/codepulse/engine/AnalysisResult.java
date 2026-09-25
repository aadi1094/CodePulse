package dev.codepulse.engine;

import java.util.List;
import java.util.Objects;

/**
 * The engine's complete, immutable output for one workspace.
 *
 * @param inventory the whole-workspace file inventory (all files, not only Java)
 * @param javaFiles one analysis per eligible Java file, in path order
 * @param importGraph observed explicit imports between the parsed files ({@code explicit-import-v1})
 * @param assessments one {@code structural-v1} assessment per eligible Java file, in the same order
 * @param findings    rule-triggered notes for all files, sorted by path, then line, then rule
 */
public record AnalysisResult(InventoryResult inventory, List<JavaFileAnalysis> javaFiles, ImportGraph importGraph,
                             List<RiskAssessment> assessments, List<Finding> findings) {

    public AnalysisResult {
        javaFiles = List.copyOf(javaFiles);
        assessments = List.copyOf(assessments);
        findings = List.copyOf(findings);
        Objects.requireNonNull(importGraph);
        if (assessments.size() != javaFiles.size()) {
            throw new IllegalArgumentException("every eligible file needs exactly one assessment");
        }
        for (int i = 0; i < javaFiles.size(); i++) {
            JavaFileAnalysis file = javaFiles.get(i);
            RiskAssessment assessment = assessments.get(i);
            if (!file.relativePath().equals(assessment.relativePath())
                || (file.parseStatus() == ParseStatus.PARSED) == (assessment.score() == null)) {
                throw new IllegalArgumentException("assessment does not match its file: " + file.relativePath());
            }
        }
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

    /** @return this file's findings in report order; empty when it has none */
    public List<Finding> findings(String relativePath) {
        List<Finding> own = new java.util.ArrayList<>();
        for (Finding finding : findings) {
            if (finding.relativePath().equals(relativePath)) {
                own.add(finding);
            }
        }
        return List.copyOf(own);
    }

    /** @return the assessment of one eligible file, or null if the path is not an eligible Java file */
    public RiskAssessment assessment(String relativePath) {
        for (RiskAssessment assessment : assessments) {
            if (assessment.relativePath().equals(relativePath)) {
                return assessment;
            }
        }
        return null;
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
