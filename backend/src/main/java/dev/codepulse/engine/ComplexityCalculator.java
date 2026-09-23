package dev.codepulse.engine;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.List;

/**
 * CodePulse cyclomatic-style complexity for one method or constructor body (Blueprint 9.4).
 *
 * <p>This is a deterministic source heuristic with a published convention. It is not guaranteed to
 * equal every other tool's control-flow-graph McCabe number.
 *
 * <p>Rules: start at 1. Add 1 for each if, for, for-each, while, do-while, catch, ternary, and each
 * short-circuit {@code &&} or {@code ||}. Add 1 for each non-default switch entry that has labels
 * (a grouped {@code case A, B ->} is one entry), and 1 for a {@code when} guard. else, try, finally,
 * return, and throw add nothing.
 *
 * <p>Lambda bodies and classes declared inside the body (local classes, anonymous class bodies) are
 * separate units: this visitor does not step into them, so their decisions are never counted twice.
 */
public final class ComplexityCalculator extends VoidVisitorAdapter<Void> {

    private int complexity = 1;   // the single straight-through path

    private ComplexityCalculator() {
    }

    /** @param body the body block of a method or constructor */
    public static int complexityOf(Node body) {
        ComplexityCalculator calculator = new ComplexityCalculator();
        body.accept(calculator, null);
        return calculator.complexity;
    }

    /** @return the highest complexity among methods with a body; 0 if there are none */
    public static int maxMethodComplexity(List<MethodMeasurement> methods) {
        int max = 0;
        for (MethodMeasurement method : methods) {
            if (method.complexity() != null && method.complexity() > max) {
                max = method.complexity();
            }
        }
        return max;
    }

    // ---- decisions: +1 each, then keep walking (conditions can hold more decisions) ----

    @Override
    public void visit(IfStmt node, Void arg) {
        complexity++;            // an "else if" is just another IfStmt inside the else branch
        super.visit(node, arg);
    }

    @Override
    public void visit(ForStmt node, Void arg) {
        complexity++;
        super.visit(node, arg);
    }

    @Override
    public void visit(ForEachStmt node, Void arg) {
        complexity++;
        super.visit(node, arg);
    }

    @Override
    public void visit(WhileStmt node, Void arg) {
        complexity++;
        super.visit(node, arg);
    }

    @Override
    public void visit(DoStmt node, Void arg) {
        complexity++;
        super.visit(node, arg);
    }

    @Override
    public void visit(CatchClause node, Void arg) {
        complexity++;            // "catch (A | B e)" is one clause; its "|" is not a BinaryExpr
        super.visit(node, arg);
    }

    @Override
    public void visit(ConditionalExpr node, Void arg) {
        complexity++;            // cond ? a : b
        super.visit(node, arg);
    }

    @Override
    public void visit(BinaryExpr node, Void arg) {
        // Only short-circuit operators. Bitwise & and | (BINARY_AND, BINARY_OR) always evaluate both sides.
        if (node.getOperator() == BinaryExpr.Operator.AND || node.getOperator() == BinaryExpr.Operator.OR) {
            complexity++;
        }
        super.visit(node, arg);
    }

    @Override
    public void visit(SwitchEntry node, Void arg) {
        if (!node.isDefault() && !node.getLabels().isEmpty()) {
            complexity++;        // "case 1, 2 ->" has two labels but is one entry: +1
        }
        if (node.getGuard().isPresent()) {
            complexity++;        // "case Integer i when ..." : the guard is one more decision
        }
        super.visit(node, arg);  // continues into the guard, so && inside it is counted too
    }

    // ---- separate units: do NOT step inside ----

    @Override
    public void visit(LambdaExpr node, Void arg) {
        // no super.visit: a lambda body is its own execution unit (MVP does not score it)
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration node, Void arg) {
        // local class inside the body: its methods are measured on their own
    }

    @Override
    public void visit(EnumDeclaration node, Void arg) {
    }

    @Override
    public void visit(RecordDeclaration node, Void arg) {
    }

    @Override
    public void visit(AnnotationDeclaration node, Void arg) {
    }

    @Override
    public void visit(ObjectCreationExpr node, Void arg) {
        // Visit what is evaluated HERE (the scope and constructor arguments), but not an anonymous
        // class body: its methods are measured on their own.
        node.getScope().ifPresent(scope -> scope.accept(this, arg));
        for (Expression argument : node.getArguments()) {
            argument.accept(this, arg);
        }
    }
}
