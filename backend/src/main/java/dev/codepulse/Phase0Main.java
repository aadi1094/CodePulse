package dev.codepulse;

import dev.codepulse.engine.FileMetrics;
import dev.codepulse.engine.PackageSummary;

import java.util.List;

/**
 * Phase 0 entry point. Builds hand-written sample metrics and prints the sorted summary.
 * This class is a learning aid and will be removed when Spring Boot arrives in Phase 4.
 */
public final class Phase0Main {

    public static void main(String[] args) {
        // Deliberately inserted out of order: the summary must sort regardless.
        List<FileMetrics> sample = List.of(
            new FileMetrics("src/main/java/dev/codepulse/engine/PackageSummary.java", "dev.codepulse.engine", 95, 5),
            new FileMetrics("src/main/java/dev/codepulse/Phase0Main.java", "dev.codepulse", 25, 1),
            new FileMetrics("src/main/java/dev/codepulse/engine/FileMetrics.java", "dev.codepulse.engine", 40, 0),
            new FileMetrics("Scratch.java", "", 8, 1)
        );

        PackageSummary summary = new PackageSummary(sample);
        System.out.print(summary.render());
    }
}
