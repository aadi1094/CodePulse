package dev.codepulse.engine;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Walks a parsed tree and produces one {@link MethodMeasurement} per explicit method or constructor.
 *
 * <p>The visitor keeps a STACK of owner labels. Entering a type (or anonymous body) pushes its label;
 * leaving pops it. The top of the stack is always the owner of whatever method we meet next. Because
 * the push happens before {@code super.visit} and the pop after it, nesting of any depth works.
 */
public final class MethodCollector extends VoidVisitorAdapter<Void> {

    private final Deque<String> owners = new ArrayDeque<>();
    private final List<MethodMeasurement> methods = new ArrayList<>();

    private MethodCollector() {
    }

    /**
     * @return methods sorted by begin line, then owner, then signature
     * @throws IllegalStateException if two methods share an identity (should be impossible)
     */
    public static List<MethodMeasurement> collect(CompilationUnit tree) {
        MethodCollector collector = new MethodCollector();
        tree.accept(collector, null);
        List<MethodMeasurement> result = new ArrayList<>(collector.methods);
        result.sort(Comparator.comparingInt(MethodMeasurement::beginLine)
            .thenComparing(MethodMeasurement::ownerLabel)
            .thenComparing(MethodMeasurement::signature));
        rejectDuplicateIdentities(result);
        return List.copyOf(result);
    }

    // ---- owners: types and anonymous bodies ----

    @Override
    public void visit(ClassOrInterfaceDeclaration node, Void arg) {
        owners.push(typeLabel(node));
        super.visit(node, arg);
        owners.pop();
    }

    @Override
    public void visit(EnumDeclaration node, Void arg) {
        owners.push(typeLabel(node));
        super.visit(node, arg);
        owners.pop();
    }

    @Override
    public void visit(RecordDeclaration node, Void arg) {
        owners.push(typeLabel(node));
        super.visit(node, arg);
        owners.pop();
    }

    @Override
    public void visit(AnnotationDeclaration node, Void arg) {
        owners.push(typeLabel(node));
        super.visit(node, arg);
        owners.pop();
    }

    @Override
    public void visit(ObjectCreationExpr node, Void arg) {
        if (node.getAnonymousClassBody().isEmpty()) {
            super.visit(node, arg);            // plain "new X()": not an owner
            return;
        }
        owners.push(owners.peek() + "#anonymous@" + beginLine(node));
        super.visit(node, arg);
        owners.pop();
    }

    @Override
    public void visit(EnumConstantDeclaration node, Void arg) {
        if (node.getClassBody().isEmpty()) {
            super.visit(node, arg);            // plain constant like RED: not an owner
            return;
        }
        owners.push(owners.peek() + "#" + node.getNameAsString());
        super.visit(node, arg);
        owners.pop();
    }

    /** Top-level: "Shop". Member of a type: "Shop.Size". Declared anywhere else (local): "Shop#Local". */
    private String typeLabel(TypeDeclaration<?> type) {
        String name = type.getNameAsString();
        if (owners.isEmpty()) {
            return name;
        }
        boolean isMemberOfAType = type.getParentNode()
            .map(parent -> parent instanceof TypeDeclaration<?>)
            .orElse(false);
        return owners.peek() + (isMemberOfAType ? "." : "#") + name;
    }

    // ---- executables ----

    @Override
    public void visit(MethodDeclaration node, Void arg) {
        add(node, signature(node.getNameAsString(), node.getParameters()),
            DeclarationKind.METHOD, node.getBody().orElse(null));
        super.visit(node, arg);                // local/anonymous classes inside the body
    }

    @Override
    public void visit(ConstructorDeclaration node, Void arg) {
        add(node, signature(node.getNameAsString(), node.getParameters()),
            DeclarationKind.CONSTRUCTOR, node.getBody());
        super.visit(node, arg);
    }

    @Override
    public void visit(CompactConstructorDeclaration node, Void arg) {
        // A compact constructor writes no parameter list; its parameters are the record components.
        RecordDeclaration record = (RecordDeclaration) node.getParentNode().orElseThrow();
        add(node, signature(node.getNameAsString(), record.getParameters()),
            DeclarationKind.COMPACT_CONSTRUCTOR, node.getBody());
        super.visit(node, arg);
    }

    /** @param body the body block, or null for an abstract/interface method without one */
    private void add(Node node, String signature, DeclarationKind kind, Node body) {
        int ncloc = LineMetricsCalculator.codeLinesIn(node.getTokenRange().orElseThrow());
        Integer complexity = body == null ? null : ComplexityCalculator.complexityOf(body);
        methods.add(new MethodMeasurement(owners.peek(), signature, kind, body != null,
            beginLine(node), node.getEnd().orElseThrow().line, ncloc, complexity));
    }

    /**
     * "price(int, int)", "format(String, Object...)". Each type is JavaParser's normalized rendering
     * of the declared type: spaces and comments inside it are dropped ("Map< String , Integer >"
     * becomes "Map<String,Integer>"), so reformatting code does not change a method's identity.
     * The separator between parameters is always ", ".
     */
    private static String signature(String name, NodeList<Parameter> parameters) {
        List<String> types = new ArrayList<>();
        for (Parameter parameter : parameters) {
            types.add(parameter.getType().asString() + (parameter.isVarArgs() ? "..." : ""));
        }
        return name + "(" + String.join(", ", types) + ")";
    }

    private static int beginLine(Node node) {
        return node.getBegin().orElseThrow().line;
    }

    private static void rejectDuplicateIdentities(List<MethodMeasurement> methods) {
        Set<String> seen = new HashSet<>();
        for (MethodMeasurement m : methods) {
            String identity = m.ownerLabel() + "|" + m.signature() + "|" + m.beginLine();
            if (!seen.add(identity)) {
                throw new IllegalStateException("duplicate method identity: " + identity);
            }
        }
    }
}
