package dev.codepulse.engine;

/**
 * One regular file found during inventory.
 *
 * @param relativePath path relative to the scanned root, always using "/" (POSIX style), e.g.
 *                     {@code src/main/java/App.java}. This is a logical name, never a server path.
 * @param sizeBytes    file size in bytes as reported by the filesystem
 */
public record SourceFile(String relativePath, long sizeBytes) {

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
    }
}
