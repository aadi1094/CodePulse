package dev.codepulse.engine;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Learning tests about equality and hash-based collections.
 *
 * <p>Why this matters for CodePulse: Phase 3 stores import-graph edges in sets. If edge equality
 * were wrong, the same edge could appear twice or a lookup could silently miss. These tests show
 * the three cases: value equality done right (records), no equality defined (plain class), and
 * equality defined on a mutable field (dangerous).
 */
class CollectionEqualityTest {

    @Test
    void recordsWithSameValuesAreEqualAndDeduplicateInAHashSet() {
        FileMetrics a = new FileMetrics("A.java", "p", 10, 1);
        FileMetrics b = new FileMetrics("A.java", "p", 10, 1);

        // Different objects in memory ...
        assertFalse(a == b, "== compares references, and these are two separate objects");
        // ... but equal by value because the record generated equals/hashCode over all components.
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        Set<FileMetrics> set = new HashSet<>();
        set.add(a);
        set.add(b);
        assertEquals(1, set.size(), "HashSet treats equal records as the same element");
        assertTrue(set.contains(new FileMetrics("A.java", "p", 10, 1)));
    }

    @Test
    void aClassWithoutEqualsIsComparedByIdentityOnly() {
        NoEqualsFile a = new NoEqualsFile("A.java");
        NoEqualsFile b = new NoEqualsFile("A.java");

        // Same content, but Object.equals compares references, so they are "different".
        assertNotEquals(a, b);

        Set<NoEqualsFile> set = new HashSet<>();
        set.add(a);
        set.add(b);
        assertEquals(2, set.size(), "without equals/hashCode the set cannot recognise duplicates");
    }

    @Test
    void mutatingAKeyAfterInsertionBreaksHashSetLookup() {
        MutableFileKey key = new MutableFileKey("A.java");
        Set<MutableFileKey> set = new HashSet<>();
        set.add(key);
        assertTrue(set.contains(key), "found while the key is unchanged");

        // The set stored the key in the bucket for hashCode("A.java").
        key.setPath("B.java");

        // Now hashCode("B.java") points at a different bucket, so the set looks in the wrong place.
        assertFalse(set.contains(key), "the very same object is no longer found");
        assertEquals(1, set.size(), "yet the element is still inside the set");
        assertFalse(set.remove(key), "and it cannot even be removed");
        // This element is now stranded: unreachable by lookup, but occupying space forever.
    }

    /** Plain class with no equals/hashCode: two instances are never equal, whatever their content. */
    static final class NoEqualsFile {
        private final String path;

        NoEqualsFile(String path) {
            this.path = path;
        }
    }

    /** A key whose equality depends on a field that can change. Do not write classes like this. */
    static final class MutableFileKey {
        private String path;

        MutableFileKey(String path) {
            this.path = path;
        }

        void setPath(String path) {
            this.path = path;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MutableFileKey that)) {
                return false;
            }
            return path.equals(that.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path);
        }
    }
}
