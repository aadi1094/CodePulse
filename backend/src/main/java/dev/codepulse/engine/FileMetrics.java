package dev.codepulse.engine;

import java.util.Objects;

/**
 * Structural measurements for one Java source file.
 *
 * <p>Phase 0: the values are hand-constructed learning data. Phase 2 will produce them by parsing
 * real source. The field names follow {@code java-metrics-v1} (Blueprint section 9.3) so later
 * phases extend this record instead of renaming it.
 *
 * <p>A record is an immutable value type: the compiler generates the constructor, one accessor per
 * component ({@code relativePath()} etc.), and {@code equals}, {@code hashCode}, {@code toString}
 * based on all components. Two FileMetrics with the same four values are equal.
 *
 * @param relativePath POSIX-style path relative to the repository root, e.g. {@code src/main/java/App.java}
 * @param packageName  declared Java package; empty string for the default package
 * @param physicalLoc  number of physical lines in the file
 * @param methodCount  explicit method declarations in the file
 */
public record FileMetrics(String relativePath, String packageName, int physicalLoc, int methodCount) {

    /**
     * Compact constructor: runs before the fields are assigned. It validates the inputs so an
     * invalid FileMetrics can never exist. Fail fast here rather than discovering bad data later.
     */
    public FileMetrics {
        Objects.requireNonNull(relativePath, "relativePath must not be null");
        Objects.requireNonNull(packageName, "packageName must not be null");
        if (relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath must not be blank");
        }
        if (physicalLoc < 0) {
            throw new IllegalArgumentException("physicalLoc must be >= 0, was " + physicalLoc);
        }
        if (methodCount < 0) {
            throw new IllegalArgumentException("methodCount must be >= 0, was " + methodCount);
        }
    }
}
