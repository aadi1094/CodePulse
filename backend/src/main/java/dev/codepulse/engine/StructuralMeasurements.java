package dev.codepulse.engine;

/**
 * The five raw numbers {@code structural-v1} reads from a parsed file (Blueprint 10.1). Nothing
 * else influences the score. Kept as a separate record so the formula can be tested from plain
 * numbers, such as the Blueprint's 59-point PaymentService example.
 */
public record StructuralMeasurements(
    int maxMethodComplexity,
    int fileNcloc,
    int maxMethodNcloc,
    int observedFanOut,
    int observedFanIn) {

    public StructuralMeasurements {
        if (maxMethodComplexity < 0 || fileNcloc < 0 || maxMethodNcloc < 0 || observedFanOut < 0 || observedFanIn < 0) {
            throw new IllegalArgumentException("measurements must be >= 0");
        }
    }

    /** @throws IllegalArgumentException if the file is not PARSED (there is nothing to measure) */
    static StructuralMeasurements of(JavaFileAnalysis file, ImportGraph graph) {
        if (file.parseStatus() != ParseStatus.PARSED) {
            throw new IllegalArgumentException("no measurements for an unparsed file: " + file.relativePath());
        }
        return new StructuralMeasurements(file.maxMethodComplexity(), file.lines().ncloc(), file.maxMethodNcloc(),
            graph.observedFanOut(file.relativePath()), graph.observedFanIn(file.relativePath()));
    }

    int value(RiskFactorCode code) {
        return switch (code) {
            case MAX_COMPLEXITY -> maxMethodComplexity;
            case FILE_NCLOC -> fileNcloc;
            case MAX_METHOD_NCLOC -> maxMethodNcloc;
            case OBSERVED_FAN_OUT -> observedFanOut;
            case OBSERVED_FAN_IN -> observedFanIn;
        };
    }
}
