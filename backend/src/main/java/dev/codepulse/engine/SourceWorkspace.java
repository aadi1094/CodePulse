package dev.codepulse.engine;

import java.nio.file.Path;

/**
 * Where the source files for one analysis come from.
 *
 * <p>An interface is a promise: "anything that is a SourceWorkspace can tell you its root folder".
 * SourceInventory depends only on this promise, not on how the folder was produced. Today the only
 * implementation is {@link LocalDirectoryWorkspace} (a trusted local folder). In Phase 7 a second
 * implementation will hold a GitHub archive extracted into a temporary folder. Inventory will not
 * need to change when that happens.
 */
public interface SourceWorkspace {

    /** @return the directory that contains the source files */
    Path root();
}
