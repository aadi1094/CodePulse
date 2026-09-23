package dev.codepulse.engine;

/**
 * One regular file found during inventory.
 *
 * <p>Java files ({@code .java}) are measured and classified; every other file is only listed with
 * its size. For non-Java files both {@code physicalLines} and {@code role} are null.
 *
 * @param relativePath  path relative to the scanned root, always using "/" (POSIX style), e.g.
 *                      {@code src/main/java/App.java}. This is a logical name, never a server path.
 * @param sizeBytes     file size in bytes as reported by the filesystem
 * @param physicalLines physical line count ({@code physicalLoc}) for Java files, or {@code null}
 *                      when not measured. Null means "not measured", 0 means "measured, empty".
 * @param role          MAIN / TEST / OTHER_SOURCE for Java files, or {@code null} for other files
 */
public record SourceFile(String relativePath, long sizeBytes, Integer physicalLines, SourceRole role) {

    public SourceFile {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath must not be blank");
        }
        if (relativePath.startsWith("/") || relativePath.contains("\\")) {
            throw new IllegalArgumentException("relativePath must be relative and use '/': " + relativePath);
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must be >= 0, was " + sizeBytes);
        }
        if (physicalLines != null && physicalLines < 0) {
            throw new IllegalArgumentException("physicalLines must be >= 0, was " + physicalLines);
        }
        // Invariant: a file is either a measured, classified Java file, or neither.
        if ((physicalLines == null) != (role == null)) {
            throw new IllegalArgumentException("physicalLines and role must both be set or both be null: " + relativePath);
        }
    }

    /** Convenience constructor for a non-Java file: listed, not measured, not classified. */
    public static SourceFile other(String relativePath, long sizeBytes) {
        return new SourceFile(relativePath, sizeBytes, null, null);
    }

    /** @return true if this is a Java file that was measured and classified */
    public boolean isJava() {
        return role != null;
    }
}
