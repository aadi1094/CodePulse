package dev.codepulse.engine;

import java.util.List;

/**
 * What an inventory scan found.
 *
 * <p>The counts below are calculated from {@code files} each time rather than stored separately,
 * so they can never disagree with the list.
 *
 * @param files             included regular files, sorted by relative path
 * @param excludedFileCount regular files that were found but skipped because they sit inside an
 *                          excluded directory such as {@code target/}. Counted, not hidden.
 */
public record InventoryResult(List<SourceFile> files, int excludedFileCount) {

    public InventoryResult {
        // Defensive copy (Phase 0 lesson): the caller's list cannot change this result later.
        files = List.copyOf(files);
        if (excludedFileCount < 0) {
            throw new IllegalArgumentException("excludedFileCount must be >= 0");
        }
    }

    /** @return number of included .java files */
    public int javaFileCount() {
        int count = 0;
        for (SourceFile file : files) {
            if (file.isJava()) {
                count++;
            }
        }
        return count;
    }

    /** @return number of included files that are not .java */
    public int otherFileCount() {
        return files.size() - javaFileCount();
    }

    /** @return number of included .java files with the given role */
    public int javaFileCount(SourceRole role) {
        int count = 0;
        for (SourceFile file : files) {
            if (file.role() == role) {
                count++;
            }
        }
        return count;
    }
}
