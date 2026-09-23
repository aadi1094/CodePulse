package dev.codepulse.engine;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Position;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns Java source text into a syntax tree, or reports why it could not.
 *
 * <p>Parsing only READS the text as data. Nothing is compiled, loaded, or executed.
 *
 * <p>Rules (Blueprint 9.1 and 9.4):
 * <ul>
 *   <li>Language level is pinned to Java 21. Our contract is Java 21 without preview features.</li>
 *   <li>Any reported problem means PARSE_FAILED, even though JavaParser may still hand back a
 *       partially "recovered" tree. Half a tree would produce half-true metrics.</li>
 *   <li>Unnamed ("compact") classes are a Java 21 preview feature that JavaParser accepts at the
 *       JAVA_21 level, so we reject them ourselves.</li>
 *   <li>Diagnostics keep only a code and position, never the parser's message.</li>
 * </ul>
 *
 * <p>Each instance owns its own configured {@link JavaParser}; nothing uses the global
 * {@code StaticJavaParser}. An instance is not safe to share between threads.
 */
public final class JavaSourceParser {

    /** At most this many diagnostics are kept per file; the total is still counted. */
    static final int MAX_DIAGNOSTICS = 10;

    private final JavaParser parser;

    public JavaSourceParser() {
        ParserConfiguration configuration = new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
            .setCharacterEncoding(StandardCharsets.UTF_8);
        this.parser = new JavaParser(configuration);
    }

    public ParseOutcome parse(String sourceText) {
        ParseResult<CompilationUnit> result = parser.parse(sourceText);

        if (!result.isSuccessful()) {
            List<Problem> problems = result.getProblems();
            List<ParseDiagnostic> diagnostics = new ArrayList<>();
            for (int i = 0; i < problems.size() && i < MAX_DIAGNOSTICS; i++) {
                diagnostics.add(toDiagnostic(problems.get(i)));
            }
            return ParseOutcome.failed(diagnostics, problems.size());
        }

        CompilationUnit tree = result.getResult().orElseThrow();
        List<ParseDiagnostic> unsupported = new ArrayList<>();
        for (ClassOrInterfaceDeclaration type : tree.findAll(ClassOrInterfaceDeclaration.class)) {
            if (type.isCompact()) {
                unsupported.add(diagnosticAt(DiagnosticCode.UNSUPPORTED_SYNTAX, type.getBegin()));
            }
        }
        if (!unsupported.isEmpty()) {
            return ParseOutcome.failed(unsupported, unsupported.size());
        }
        return ParseOutcome.parsed(tree);
    }

    private static ParseDiagnostic toDiagnostic(Problem problem) {
        // Each step may be missing, so each returns an Optional. flatMap/map continue only when a
        // value is present; otherwise the whole chain is empty and we record an unknown position.
        Optional<Position> begin = problem.getLocation()
            .flatMap(tokenRange -> tokenRange.getBegin().getRange())
            .map(range -> range.begin);
        return diagnosticAt(DiagnosticCode.SYNTAX_ERROR, begin);
        // problem.getMessage() is deliberately NOT stored: it can quote the analyzed source.
    }

    private static ParseDiagnostic diagnosticAt(DiagnosticCode code, Optional<Position> position) {
        if (position.isEmpty()) {
            return new ParseDiagnostic(code, null, null);
        }
        return new ParseDiagnostic(code, position.get().line, position.get().column);
    }
}
