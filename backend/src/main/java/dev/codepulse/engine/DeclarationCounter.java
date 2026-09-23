package dev.codepulse.engine;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

/**
 * Walks a syntax tree once and counts declarations ({@code java-metrics-v1}, Blueprint 9.3).
 *
 * <p>How a visitor works: {@link VoidVisitorAdapter} already knows how to walk every kind of node
 * and visit all its children. We override only the {@code visit} methods for the node kinds we care
 * about. Each override counts, then calls {@code super.visit(...)} so the walk continues into that
 * node's children. Forgetting {@code super.visit} is the classic bug: everything inside that node
 * (nested classes, methods of local classes, lambdas) would silently be skipped.
 *
 * <p>Every node is visited exactly once, so nested declarations are counted once each, never
 * twice. A fresh counter is created per file because it holds mutable tallies.
 */
public final class DeclarationCounter extends VoidVisitorAdapter<Void> {

    private int classCount;
    private int interfaceCount;
    private int enumCount;
    private int recordCount;
    private int annotationCount;
    private int anonymousClassCount;
    private int methodCount;
    private int constructorCount;
    private int executableCount;
    private int lambdaCount;

    private DeclarationCounter() {
    }

    /** Counts declarations in a successfully parsed tree. Never call this with a recovered tree. */
    public static DeclarationCounts countIn(CompilationUnit tree) {
        DeclarationCounter counter = new DeclarationCounter();
        tree.accept(counter, null);   // start the walk at the root
        return new DeclarationCounts(
            counter.classCount, counter.interfaceCount, counter.enumCount, counter.recordCount,
            counter.annotationCount, counter.anonymousClassCount, counter.methodCount,
            counter.constructorCount, counter.executableCount, counter.lambdaCount);
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration node, Void arg) {
        // JavaParser uses one node type for both; the flag tells them apart.
        if (node.isInterface()) {
            interfaceCount++;
        } else {
            classCount++;
        }
        super.visit(node, arg);
    }

    @Override
    public void visit(EnumDeclaration node, Void arg) {
        enumCount++;
        super.visit(node, arg);
    }

    @Override
    public void visit(RecordDeclaration node, Void arg) {
        recordCount++;
        super.visit(node, arg);
    }

    @Override
    public void visit(AnnotationDeclaration node, Void arg) {
        annotationCount++;
        // Its members (e.g. "String value();") are AnnotationMemberDeclaration nodes, not methods.
        super.visit(node, arg);
    }

    @Override
    public void visit(ObjectCreationExpr node, Void arg) {
        // "new Runnable() { ... }" has a body; plain "new Item(...)" does not.
        if (node.getAnonymousClassBody().isPresent()) {
            anonymousClassCount++;
        }
        super.visit(node, arg);
    }

    @Override
    public void visit(MethodDeclaration node, Void arg) {
        methodCount++;
        if (node.getBody().isPresent()) {     // abstract and plain interface methods have no body
            executableCount++;
        }
        super.visit(node, arg);
    }

    @Override
    public void visit(ConstructorDeclaration node, Void arg) {
        constructorCount++;
        executableCount++;                    // a constructor always has a body
        super.visit(node, arg);
    }

    @Override
    public void visit(CompactConstructorDeclaration node, Void arg) {
        constructorCount++;                   // record Item(...) { Item { ... } }
        executableCount++;
        super.visit(node, arg);
    }

    @Override
    public void visit(LambdaExpr node, Void arg) {
        lambdaCount++;
        super.visit(node, arg);
    }
}
