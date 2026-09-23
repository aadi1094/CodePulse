package dev.codepulse;

import dev.codepulse.engine.InventoryResult;
import dev.codepulse.engine.LocalDirectoryWorkspace;
import dev.codepulse.engine.SourceFile;
import dev.codepulse.engine.SourceInventory;
import dev.codepulse.engine.SourceRole;

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
        InventoryResult result = new SourceInventory().scan(new LocalDirectoryWorkspace(Path.of(args[0])));
        for (SourceFile file : result.files()) {
            String details = file.isJava()
                ? file.role() + "  " + file.physicalLines() + " lines"
                : "not java";
            System.out.println(file.relativePath() + "  " + file.sizeBytes() + " bytes  " + details);
        }
        System.out.println();
        System.out.println("java files:     " + result.javaFileCount()
            + "  (MAIN " + result.javaFileCount(SourceRole.MAIN)
            + ", TEST " + result.javaFileCount(SourceRole.TEST)
            + ", OTHER_SOURCE " + result.javaFileCount(SourceRole.OTHER_SOURCE) + ")");
        System.out.println("other files:    " + result.otherFileCount());
        System.out.println("excluded files: " + result.excludedFileCount());
    }
}
