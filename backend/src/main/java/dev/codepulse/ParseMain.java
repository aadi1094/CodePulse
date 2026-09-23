package dev.codepulse;

import com.github.javaparser.printer.YamlPrinter;
import dev.codepulse.engine.JavaSourceParser;
import dev.codepulse.engine.ParseDiagnostic;
import dev.codepulse.engine.ParseOutcome;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Developer-only harness: parse ONE trusted local .java file and print the outcome.
 * Add --tree to print the syntax tree, which is a good way to learn what an AST looks like.
 * Never exposed over HTTP.
 *
 * <pre>java -cp "target/classes:$(cat target/classpath.txt)" dev.codepulse.ParseMain some/File.java --tree</pre>
 */
public final class ParseMain {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: ParseMain <file.java> [--tree]");
            System.exit(2);
        }
        String source = Files.readString(Path.of(args[0]));   // UTF-8; trusted local file only
        ParseOutcome outcome = new JavaSourceParser().parse(source);

        System.out.println("status: " + outcome.status());
        if (!outcome.diagnostics().isEmpty()) {
            System.out.println("problems: " + outcome.problemCount());
            for (ParseDiagnostic d : outcome.diagnostics()) {
                String where = d.line() == null ? "unknown position" : "line " + d.line() + ", column " + d.column();
                System.out.println("  " + d.code() + " at " + where);
            }
        }
        boolean showTree = args.length > 1 && args[1].equals("--tree");
        if (showTree) {
            outcome.syntaxTree().ifPresentOrElse(
                tree -> System.out.println(new YamlPrinter(true).output(tree)),
                () -> System.out.println("(no tree: the file did not parse)"));
        }
    }
}
