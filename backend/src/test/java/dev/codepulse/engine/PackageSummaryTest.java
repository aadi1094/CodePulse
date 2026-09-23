package dev.codepulse.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageSummaryTest {

    @Test
    void groupsFilesByPackageInSortedOrder() {
        // Inserted unsorted on purpose.
        List<FileMetrics> input = List.of(
            new FileMetrics("engine/B.java", "dev.codepulse.engine", 10, 1),
            new FileMetrics("Main.java", "dev.codepulse", 5, 1),
            new FileMetrics("engine/A.java", "dev.codepulse.engine", 20, 2)
        );

        Map<String, Set<String>> grouped = new PackageSummary(input).groupByPackage();

        // Packages come out sorted by name.
        assertEquals(List.of("dev.codepulse", "dev.codepulse.engine"), new ArrayList<>(grouped.keySet()));
        // Set equality is by content, not by implementation class: TreeSet equals Set.of(...)
        assertEquals(Set.of("engine/A.java", "engine/B.java"), grouped.get("dev.codepulse.engine"));
        assertEquals(Set.of("Main.java"), grouped.get("dev.codepulse"));
        // Files inside a package come out sorted too.
        assertEquals(List.of("engine/A.java", "engine/B.java"), new ArrayList<>(grouped.get("dev.codepulse.engine")));
    }

    @Test
    void emptyInputProducesEmptySummary() {
        PackageSummary summary = new PackageSummary(List.of());

        assertTrue(summary.groupByPackage().isEmpty());
        assertEquals("", summary.render());
    }

    @Test
    void isNotAffectedByLaterChangesToTheInputList() {
        List<FileMetrics> input = new ArrayList<>();
        input.add(new FileMetrics("A.java", "p", 1, 0));

        PackageSummary summary = new PackageSummary(input);
        // The caller keeps mutating their own list after handing it over.
        input.add(new FileMetrics("B.java", "p", 1, 0));
        input.clear();

        // The summary copied the list in its constructor, so it still sees exactly one file.
        assertEquals(Set.of("A.java"), summary.groupByPackage().get("p"));
    }

    @Test
    void rejectsDuplicateRelativePaths() {
        List<FileMetrics> input = List.of(
            new FileMetrics("A.java", "p", 1, 0),
            new FileMetrics("A.java", "p", 2, 0)
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new PackageSummary(input));
        assertEquals("duplicate relativePath: A.java", ex.getMessage());
    }
}
