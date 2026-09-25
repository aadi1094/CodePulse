package dev.codepulse.engine;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the two facts the import graph needs from a parsed tree while the tree is still in memory:
 * which top-level types the file declares, and which imports it writes (Blueprint 9.6).
 *
 * <p>No visitor is needed. Imports and top-level types are direct children of the compilation unit,
 * so the parser already holds them in two lists.
 */
public final class ImportCollector {

    private ImportCollector() {
    }

    /**
     * Fully qualified names of the top-level types declared in the file, in source order:
     * {@code app.model.Order}, or just {@code Order} in the default package.
     *
     * <p>Only top-level types are indexed in {@code explicit-import-v1}. A nested type such as
     * {@code app.model.Order.Line} is not a canonical import target in the MVP, so an import of it
     * stays unresolved.
     */
    public static List<String> declaredTypeNames(CompilationUnit tree) {
        List<String> names = new ArrayList<>();
        for (TypeDeclaration<?> type : tree.getTypes()) {
            names.add(type.getFullyQualifiedName().orElseThrow());
        }
        return List.copyOf(names);
    }

    /** Every import declaration in source order, duplicates included. */
    public static List<ImportStatement> imports(CompilationUnit tree) {
        List<ImportStatement> result = new ArrayList<>();
        for (ImportDeclaration declaration : tree.getImports()) {
            result.add(new ImportStatement(declaration.getNameAsString(), declaration.isStatic(),
                declaration.isAsterisk(), declaration.getBegin().orElseThrow().line));
        }
        return List.copyOf(result);
    }
}
