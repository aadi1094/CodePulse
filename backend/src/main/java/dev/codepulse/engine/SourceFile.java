package dev.codepulse.engine;

/**
 * One regular file found during inventory.
 *
 * @param relativePath  path relative to the scanned root, always using "/" (POSIX style), e.g.
 *                      {@code src/main/java/App.java}. This is a logical name, never a server path.
 * @param sizeBytes     file size in bytes as reported by the filesystem
 * @param physicalLines physical line count ({@code physicalLoc}) for {@code .java} files, or
 *                      {@code null} when the file was not measured (non-Java files). Null means
 *                      "not measured", which is different from 0 ("measured, and empty").
 */
public record SourceFile(String relativePath, long sizeBytes, Integer physicalLines) {

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
    }

    /** @return true if this file's lines were counted */
    public boolean isMeasured() {
        return physicalLines != null;
    }
}
