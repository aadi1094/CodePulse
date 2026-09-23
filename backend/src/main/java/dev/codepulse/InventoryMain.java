package dev.codepulse;

import dev.codepulse.engine.InventoryResult;
import dev.codepulse.engine.SourceFile;
import dev.codepulse.engine.SourceInventory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Developer-only harness: lists the inventory of a local folder you choose.
 * Never exposed over HTTP (Blueprint 11.3: no REST endpoint accepts a server path).
 *
 * <pre>java -cp target/classes dev.codepulse.InventoryMain src</pre>
 */
public final class InventoryMain {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: InventoryMain <directory>");
            System.exit(2);
        }
        InventoryResult result = new SourceInventory().scan(Path.of(args[0]));
        for (SourceFile file : result.files()) {
            System.out.println(file.relativePath() + "  " + file.sizeBytes() + " bytes");
        }
        System.out.println("included files: " + result.files().size());
        System.out.println("excluded files: " + result.excludedFileCount());
    }
}
