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
        if (args.length > 0 && args[0].equals("--duplicate")) {
            failWithDuplicatePath();
        }

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

    /**
     * Deliberate failure for debugger practice. The second entry repeats the first path, so
     * PackageSummary's constructor throws IllegalArgumentException. The exception is not caught:
     * it unwinds through main and the JVM prints the stack trace. Read the trace bottom-up
     * (where the program started) to top (where it failed).
     */
    private static void failWithDuplicatePath() {
        List<FileMetrics> withDuplicate = List.of(
            new FileMetrics("src/main/java/App.java", "app", 30, 2),
            new FileMetrics("src/main/java/App.java", "app", 31, 2)
        );
        new PackageSummary(withDuplicate);
    }
}
