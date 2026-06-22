package com.senseei.launcher.ark.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IniMergeTest {

    private static final String SS = "[ServerSettings]";

    @Test
    void updatesExistingKeyInPlace() {
        String live = "[ServerSettings]\nDifficultyOffset=1.0\nXPMultiplier=1.0\n";
        String out = IniMerge.merge(live, "[ServerSettings]\nXPMultiplier=2.0\n", SS);
        assertTrue(out.lines().anyMatch(l -> l.equals("XPMultiplier=2.0")));
        assertTrue(out.contains("DifficultyOffset=1.0"));
        assertFalse(out.contains("XPMultiplier=1.0"));
    }

    @Test
    void appendsNewKeyToSection() {
        String live = "[ServerSettings]\nDifficultyOffset=1.0\n";
        String out = IniMerge.merge(live, "[ServerSettings]\nDinoCountMultiplier=0.5\n", SS);
        assertTrue(out.contains("DinoCountMultiplier=0.5"));
        assertTrue(out.contains("DifficultyOffset=1.0"));
    }

    @Test
    void preservesManagedKeysAndOtherSections() {
        String live = "[ServerSettings]\nServerPassword=secret\nXPMultiplier=1.0\n\n[SessionSettings]\nSessionName=Gabriel\n";
        String out = IniMerge.merge(live, "[ServerSettings]\nXPMultiplier=2.0\n", SS);
        assertTrue(out.contains("ServerPassword=secret"));
        assertTrue(out.contains("[SessionSettings]"));
        assertTrue(out.contains("SessionName=Gabriel"));
        assertTrue(out.lines().anyMatch(l -> l.equals("XPMultiplier=2.0")));
    }

    @Test
    void leavesSameKeyInOtherSectionsUntouched() {
        String live = "[Other]\nXPMultiplier=1.0\n[ServerSettings]\nXPMultiplier=1.0\n";
        String out = IniMerge.merge(live, "[ServerSettings]\nXPMultiplier=2.0\n", SS);
        assertTrue(out.contains("[Other]\nXPMultiplier=1.0"));
        assertTrue(out.contains("[ServerSettings]\nXPMultiplier=2.0"));
    }

    @Test
    void emptySourceLeavesLiveUnchanged() {
        String live = "[ServerSettings]\nXPMultiplier=1.0\n";
        assertEquals(live, IniMerge.merge(live, "[ServerSettings]\n", SS));
    }

    @Test
    void createsSectionWhenMissing() {
        String out = IniMerge.merge("[SessionSettings]\nSessionName=X\n", "[ServerSettings]\nXPMultiplier=2.0\n", SS);
        assertTrue(out.contains("[ServerSettings]"));
        assertTrue(out.contains("XPMultiplier=2.0"));
        assertTrue(out.contains("SessionName=X"));
    }

    @Test
    void stripsInlineComments() {
        String out = IniMerge.merge("[ServerSettings]\nXPMultiplier=1.0\n",
                "[ServerSettings]\nXPMultiplier=2.0   ; doubled\n", SS);
        assertTrue(out.lines().anyMatch(l -> l.equals("XPMultiplier=2.0")));
    }
}
