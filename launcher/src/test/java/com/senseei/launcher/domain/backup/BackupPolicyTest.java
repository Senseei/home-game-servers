package com.senseei.launcher.domain.backup;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackupPolicyTest {

    @Test
    void prunesEverythingBeyondKeep() {
        List<String> newestFirst = List.of("e", "d", "c", "b", "a");
        assertEquals(List.of("b", "a"), BackupPolicy.surplus(newestFirst, 3));
    }

    @Test
    void nothingToPruneWhenAtOrUnderLimit() {
        assertEquals(List.of(), BackupPolicy.surplus(List.of("b", "a"), 5));
        assertEquals(List.of(), BackupPolicy.surplus(List.of("b", "a"), 2));
    }

    @Test
    void keepZeroOrNegativeKeepsEverything() {
        assertEquals(List.of(), BackupPolicy.surplus(List.of("a", "b"), 0));
        assertEquals(List.of(), BackupPolicy.surplus(List.of("a", "b"), -1));
    }
}
