package dev.codepulse.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end through the real analyzer on a temp workspace. The hand-drawn graph for the shop
 * project (an arrow means "imports"):
 * <pre>
 *   OrderController --> OrderService --> Order
 *          |                              ^
 *          +------------------------------+
 *   Money  <-- OrderService
 * </pre>
 * Four edges. Fan-in of Order is 2, fan-out of OrderController is 2, Order imports nothing.
 */
class ImportGraphBuilderTest {

    private static final String ORDER = "src/main/java/app/model/Order.java";
    private static final String MONEY = "src/main/java/app/model/Money.java";
    private static final String SERVICE = "src/main/java/app/OrderService.java";
    private static final String CONTROLLER = "src/main/java/app/web/OrderController.java";

    @TempDir
    Path root;

    private final JavaSourceAnalyzer analyzer = new JavaSourceAnalyzer();

    private void writeShopProject() throws IOException {
        write(ORDER, "package app.model;\n\npublic class Order {\n    public static class Line {}\n}\n");
        write(MONEY, "package app.model;\n\npublic record Money(long cents) {}\n");
        write(SERVICE, """
            package app;
            import app.model.Order;
            import app.model.Money;
            import app.model.Order;
            import app.OrderService;
            import java.util.List;
            import java.util.*;
            import static java.util.Objects.requireNonNull;
            import static java.util.Collections.*;
            import app.model.Order.Line;

            public class OrderService {
            }
            """);
        write(CONTROLLER, """
            package app.web;

            import app.OrderService;
            import app.model.Order;

            public class OrderController {
            }
            """);
    }

    @Test
    void buildsTheHandDrawnEdgesInDeterministicOrder() throws IOException {
        writeShopProject();

        ImportGraph graph = analyze().importGraph();

        assertEquals("explicit-import-v1", graph.graphMode());
        assertEquals(List.of(
            new FileEdge(SERVICE, MONEY, List.of("app.model.Money"), List.of(3), false),
            new FileEdge(SERVICE, ORDER, List.of("app.model.Order"), List.of(2, 4), false),   // duplicate collapsed
            new FileEdge(CONTROLLER, SERVICE, List.of("app.OrderService"), List.of(3), false),
            new FileEdge(CONTROLLER, ORDER, List.of("app.model.Order"), List.of(4), false)),
            graph.edges());
    }

    @Test
    void fanInCountsImportersAndFanOutCountsImportedFiles() throws IOException {
        writeShopProject();

        ImportGraph graph = analyze().importGraph();

        assertEquals(2, graph.observedFanOut(CONTROLLER));
        assertEquals(0, graph.observedFanIn(CONTROLLER), "nobody imports the controller");
        assertEquals(2, graph.observedFanOut(SERVICE));
        assertEquals(1, graph.observedFanIn(SERVICE));
        assertEquals(0, graph.observedFanOut(ORDER), "measured: imports nothing");
        assertEquals(2, graph.observedFanIn(ORDER));
        assertEquals(Set.of(SERVICE, CONTROLLER), graph.importingFiles(ORDER));
        assertEquals(Set.of(MONEY, ORDER), graph.importedFiles(SERVICE));
    }

    @Test
    void everyImportLandsInExactlyOneEvidenceBucket() throws IOException {
        writeShopProject();

        ImportEvidence service = analyze().importGraph().evidence(SERVICE);

        // 9 imports: Order, Money, Order(dup), self  -> 4 resolved (self resolves, no edge)
        //            java.util.*                      -> 1 wildcard
        //            static entry, static Collections.* -> 2 static
        //            java.util.List, Order.Line       -> 2 unresolved/external
        assertEquals(new ImportEvidence(9, 4, 1, 2, 0, 2,
            List.of("java.util.List", "app.model.Order.Line"), false), service);
        assertEquals(ImportEvidence.NONE, analyze().importGraph().evidence(ORDER));
    }

    @Test
    void ambiguousTypeNamesCreateNoEdgeButAWarning() throws IOException {
        write("a/Dup.java", "package dup;\nclass Dup {}\n");
        write("b/Dup.java", "package dup;\nclass Dup {}\n");
        write("User.java", "package u;\nimport dup.Dup;\nclass User {}\n");

        ImportGraph graph = analyze().importGraph();

        assertEquals(List.of(), graph.edges(), "do not pick the first match");
        assertEquals(Map.of("dup.Dup", List.of("a/Dup.java", "b/Dup.java")), graph.ambiguousTypes());
        assertEquals(new ImportEvidence(1, 0, 0, 0, 1, 0, List.of(), false), graph.evidence("User.java"));
        assertEquals(0, graph.observedFanOut("User.java"));
    }

    @Test
    void failedFilesAreNotNodesAndTheirTypesCannotBeTargets() throws IOException {
        write("Broken.java", "package gone;\nclass Gone {\n");
        write("User.java", "package u;\nimport gone.Gone;\nclass User {}\n");

        AnalysisResult result = analyze();
        ImportGraph graph = result.importGraph();

        assertEquals(ParseStatus.PARSE_FAILED, find(result, "Broken.java").parseStatus());
        assertTrue(find(result, "Broken.java").imports().isEmpty());
        assertNull(graph.observedFanOut("Broken.java"), "not measured, so null, never 0");
        assertNull(graph.observedFanIn("Broken.java"));
        assertNull(graph.evidence("Broken.java"));
        // Without type information, a type in a broken file looks exactly like a library type.
        assertEquals(new ImportEvidence(1, 0, 0, 0, 0, 1, List.of("gone.Gone"), false), graph.evidence("User.java"));
        assertEquals(1, graph.evidenceByFile().size());
    }

    @Test
    void unresolvedExamplesAreDistinctAndCappedAtTwenty() throws IOException {
        StringBuilder source = new StringBuilder("package u;\n");
        for (int i = 1; i <= 25; i++) {
            source.append("import lib.Type").append(i).append(";\n");
        }
        source.append("import lib.Type1;\n");                       // 26th declaration, duplicate name
        source.append("class User {}\n");
        write("User.java", source.toString());

        ImportEvidence evidence = analyze().importGraph().evidence("User.java");

        assertEquals(26, evidence.totalImportDeclarations());
        assertEquals(26, evidence.unresolvedOrExternalImports());
        assertEquals(20, evidence.unresolvedExamples().size());
        assertEquals("lib.Type1", evidence.unresolvedExamples().get(0));
        assertEquals("lib.Type20", evidence.unresolvedExamples().get(19));
        assertTrue(evidence.examplesTruncated());
    }

    @Test
    void manyTypesFromOneFileAreOneEdgeWithBoundedEvidence() throws IOException {
        StringBuilder library = new StringBuilder("package lib;\n");
        StringBuilder user = new StringBuilder("package u;\n");
        for (int i = 1; i <= 22; i++) {
            library.append("class Type").append(i).append(" {}\n");
            user.append("import lib.Type").append(i).append(";\n");
        }
        user.append("class User {}\n");
        write("Lib.java", library.toString());
        write("User.java", user.toString());

        ImportGraph graph = analyze().importGraph();

        assertEquals(1, graph.edgeCount(), "22 imported types from one file collapse into one edge");
        FileEdge edge = graph.edges().get(0);
        assertEquals(20, edge.importedTypes().size());
        assertEquals(20, edge.importLines().size());
        assertTrue(edge.truncated());
        assertEquals(1, graph.observedFanOut("User.java"));
        assertEquals(22, graph.evidence("User.java").resolvedSingleTypeImports());
    }

    @Test
    void workspaceWithoutParsedFilesHasAnEmptyGraph() throws IOException {
        write("README.md", "no java\n");

        ImportGraph graph = analyze().importGraph();

        assertEquals(0, graph.edgeCount());
        assertTrue(graph.evidenceByFile().isEmpty());
        assertNull(graph.observedFanIn("anything.java"));
    }

    @Test
    void graphRejectsEdgesBetweenFilesThatAreNotParsedNodes() {
        FileEdge edge = new FileEdge("A.java", "B.java", List.of("b.B"), List.of(2), false);

        assertThrows(IllegalArgumentException.class,
            () -> new ImportGraph(List.of(edge), Map.of("A.java", ImportEvidence.NONE), Map.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new FileEdge("A.java", "A.java", List.of("a.A"), List.of(2), false), "self edge");
        assertFalse(new ImportEvidence(1, 0, 0, 0, 0, 1, List.of("x.Y"), false).examplesTruncated());
        assertThrows(IllegalArgumentException.class,
            () -> new ImportEvidence(3, 1, 1, 0, 0, 0, List.of(), false), "buckets must add up");
    }

    private AnalysisResult analyze() throws IOException {
        return analyzer.analyze(new LocalDirectoryWorkspace(root));
    }

    private static JavaFileAnalysis find(AnalysisResult result, String path) {
        for (JavaFileAnalysis f : result.javaFiles()) {
            if (f.relativePath().equals(path)) {
                return f;
            }
        }
        throw new AssertionError("not analyzed: " + path);
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }
}
