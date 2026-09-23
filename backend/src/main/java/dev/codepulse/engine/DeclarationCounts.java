package dev.codepulse.engine;

/**
 * Declaration counts for one successfully parsed file ({@code java-metrics-v1}, Blueprint 9.3).
 * Only declarations written in the file count; implicit members Java generates are never counted.
 *
 * @param classCount          named classes, including nested and local; not interfaces, not anonymous
 * @param interfaceCount      named interfaces (annotation types are counted separately)
 * @param enumCount           enum declarations
 * @param recordCount         record declarations
 * @param annotationCount     annotation type declarations ({@code @interface})
 * @param anonymousClassCount {@code new X() { ... }} bodies; informational, not classes
 * @param methodCount         explicit methods, including abstract and interface methods
 * @param constructorCount    explicit constructors, including compact record constructors
 * @param executableCount     methods and constructors that have a body
 * @param lambdaCount         lambda expressions; not included in executableCount
 */
public record DeclarationCounts(
    int classCount,
    int interfaceCount,
    int enumCount,
    int recordCount,
    int annotationCount,
    int anonymousClassCount,
    int methodCount,
    int constructorCount,
    int executableCount,
    int lambdaCount) {

    public DeclarationCounts {
        int[] all = {classCount, interfaceCount, enumCount, recordCount, annotationCount,
            anonymousClassCount, methodCount, constructorCount, executableCount, lambdaCount};
        for (int value : all) {
            if (value < 0) {
                throw new IllegalArgumentException("counts must be >= 0");
            }
        }
        if (executableCount > methodCount + constructorCount) {
            throw new IllegalArgumentException("executables are a subset of methods and constructors");
        }
    }
}
