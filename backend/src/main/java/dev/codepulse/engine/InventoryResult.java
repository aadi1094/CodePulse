package dev.codepulse.engine;

import java.util.List;

/**
 * What an inventory scan found.
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
}
